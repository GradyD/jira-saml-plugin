package com.bitium.jira.servlet;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.opensaml.xml.schema.XSAny;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.saml.SAMLCredential;
import org.springframework.security.saml.context.SAMLMessageContext;
import org.springframework.security.saml.util.SAMLUtil;
import org.springframework.security.saml.websso.WebSSOProfile;
import org.springframework.security.saml.websso.WebSSOProfileConsumer;
import org.springframework.security.saml.websso.WebSSOProfileConsumerImpl;
import org.springframework.security.saml.websso.WebSSOProfileImpl;
import org.springframework.security.saml.websso.WebSSOProfileOptions;

import com.atlassian.jira.user.DelegatingApplicationUser;
import com.atlassian.seraph.auth.Authenticator;
import com.atlassian.seraph.auth.DefaultAuthenticator;
import com.atlassian.seraph.config.SecurityConfigFactory;
import com.bitium.jira.config.SAMLJiraConfig;
import com.bitium.saml.SAMLContext;


public class SsoLoginServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private Log log = LogFactory.getLog(SsoLoginServlet.class);

	private SAMLJiraConfig saml2Config;

	@Override
	public void init() throws ServletException {
		super.init();
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		try {
			SAMLContext context = new SAMLContext(request, saml2Config);
			SAMLMessageContext messageContext = context.createSamlMessageContext(request, response);

			// Generate options for the current SSO request
	        WebSSOProfileOptions options = new WebSSOProfileOptions();
	        options.setBinding(org.opensaml.common.xml.SAMLConstants.SAML2_REDIRECT_BINDING_URI);
                options.setIncludeScoping(false);

			// Send request
	        WebSSOProfile webSSOprofile = new WebSSOProfileImpl(context.getSamlProcessor(), context.getMetadataManager());
	        webSSOprofile.sendAuthenticationRequest(messageContext, options);
		} catch (Exception e) {
		    log.error("saml plugin error + " + e.getMessage());
			response.sendRedirect("/jira/login.jsp?samlerror=general");
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException {
		try {
			SAMLContext context = new SAMLContext(request, saml2Config);
			SAMLMessageContext messageContext = context.createSamlMessageContext(request, response);

			// Process response
	        context.getSamlProcessor().retrieveMessage(messageContext);

	        messageContext.setLocalEntityEndpoint(SAMLUtil.getEndpoint(messageContext.getLocalEntityRoleMetadata().getEndpoints(), messageContext.getInboundSAMLBinding(), request.getRequestURL().toString()));
	        messageContext.getPeerEntityMetadata().setEntityID(saml2Config.getIdpEntityId());

	        WebSSOProfileConsumer consumer = new WebSSOProfileConsumerImpl(context.getSamlProcessor(), context.getMetadataManager());
	        SAMLCredential credential = consumer.processAuthenticationResponse(messageContext);

	        request.getSession().setAttribute("SAMLCredential", credential);

	        //String userName = ((XSAny)credential.getAttributes().get(0).getAttributeValues().get(0)).getTextContent();
                String userName = credential.getNameID().getValue(); 

	        authenticateUserAndLogin(request, response, userName);
		} catch (AuthenticationException e) {
			try {
			    log.error("saml plugin error + " + e.getMessage());
				response.sendRedirect("/jira/login.jsp?samlerror=plugin_exception");
			} catch (IOException e1) {
				throw new ServletException();
			}
		} catch (Exception e) {
			try {
			    log.error("saml plugin error + " + e.getMessage());
				response.sendRedirect("/jira/login.jsp?samlerror=plugin_exception");
			} catch (IOException e1) {
				throw new ServletException();
			}
		}
	}

	private void authenticateUserAndLogin(HttpServletRequest request,
			HttpServletResponse response, String username)
			throws NoSuchMethodException, IllegalAccessException,
			InvocationTargetException, IOException {
		Authenticator authenticator = SecurityConfigFactory.getInstance().getAuthenticator();

		if (authenticator instanceof DefaultAuthenticator) {
			//DefaultAuthenticator defaultAuthenticator = (DefaultAuthenticator)authenticator;

		    Method getUserMethod = DefaultAuthenticator.class.getDeclaredMethod("getUser", new Class[]{String.class});
		    getUserMethod.setAccessible(true);
		    Object userObject = getUserMethod.invoke(authenticator, new Object[]{username});
		    if(userObject != null && userObject instanceof DelegatingApplicationUser) {
		    	Principal principal = (Principal)userObject;

		    	Method authUserMethod = DefaultAuthenticator.class.getDeclaredMethod("authoriseUserAndEstablishSession",
		    			new Class[]{HttpServletRequest.class, HttpServletResponse.class, Principal.class});
		    	authUserMethod.setAccessible(true);
		    	Boolean result = (Boolean)authUserMethod.invoke(authenticator, new Object[]{request, response, principal});

		        if (result) {
		        	response.sendRedirect("/jira/secure/Dashboard.jspa");
		        	return;
		        }
		    }
		}

		response.sendRedirect("/jira/login.jsp?samlerror=user_not_found");
	}

	public void setSaml2Config(SAMLJiraConfig saml2Config) {
		this.saml2Config = saml2Config;
	}


}
