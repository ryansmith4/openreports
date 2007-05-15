/*
 * Copyright (C) 2003 Erik Swenson - erik@oreports.com
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *  
 */

package org.efs.openreports.providers;

import java.util.ArrayList;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;

import org.apache.log4j.Logger;

import org.efs.openreports.objects.MailMessage;
import org.efs.openreports.objects.ORProperty;
import org.efs.openreports.util.SMTPAuthenticator;

public class MailProvider 
{
	protected static Logger log = Logger.getLogger(MailProvider.class.getName());

	private PropertiesProvider propertiesProvider;

	private String mailHost;
	private boolean useMailAuthenticator;
	private String userName;
	private String password;	
	
	public MailProvider(PropertiesProvider propertiesProvider) throws ProviderException
	{
		this.propertiesProvider = propertiesProvider;
		init();
	}
	
	protected void init() throws ProviderException
	{
		mailHost = null;
		useMailAuthenticator = false;
		userName = null;
		password = null;

		ORProperty property = propertiesProvider.getProperty(ORProperty.MAIL_SMTP_HOST);
		if (property != null) mailHost = property.getValue();

		property = propertiesProvider.getProperty(ORProperty.MAIL_SMTP_AUTH);		
		if (property != null) useMailAuthenticator = Boolean.valueOf(property.getValue()).booleanValue();
		
		property = propertiesProvider.getProperty(ORProperty.MAIL_AUTH_USER);
		if (property != null) userName = property.getValue();
		
		property = propertiesProvider.getProperty(ORProperty.MAIL_AUTH_PASSWORD);
		if (property != null) password = property.getValue();
		
		log.info("Created: Use Mail Authenticator = " + useMailAuthenticator);
	}

	public void sendMail(MailMessage mail) throws ProviderException
	{
		try
		{
			// create a session
			Properties mailProps = new Properties();
			mailProps.put("mail.smtp.host", mailHost);

			Session mailSession = null;
			if (useMailAuthenticator) 
			{
				mailSession = Session.getInstance(mailProps, new SMTPAuthenticator(userName, password));				
			}
			else
			{
				mailSession = Session.getInstance(mailProps, null);
			}				

			// construct internet addresses
			InternetAddress from = new InternetAddress(mail.getSender());

			ArrayList recipients = mail.getRecipients();
			InternetAddress[] to = new InternetAddress[recipients.size()];
			for (int i = 0; i < recipients.size(); i++)
			{
				to[i] = new InternetAddress((String) recipients.get(i));
			}

			// create multipart
			Multipart multipart = new MimeMultipart();

			// add text part
			if (mail.getText() != null)
			{
				MimeBodyPart mbpText = new MimeBodyPart();
				mbpText.setText(mail.getText());
				multipart.addBodyPart(mbpText);
			}

			// add file attachments
			ArrayList attachments = mail.getAttachments();
			for (int i = 0; i < attachments.size(); i++)
			{
				String fileAttachment = (String) attachments.get(i);
				FileDataSource source = new FileDataSource(fileAttachment);

				MimeBodyPart mbpAttachment = new MimeBodyPart();
				mbpAttachment.setDataHandler(new DataHandler(source));
				mbpAttachment.setFileName(fileAttachment);

				multipart.addBodyPart(mbpAttachment);
			}

			// add byteArrayAttachment
			if (mail.getByteArrayDataSource() != null)
			{
				String contentType = mail.getByteArrayDataSource().getContentType();
				if (contentType != null && contentType.equals("text/html"))
				{
					Multipart htmlMP = new MimeMultipart("related");
					MimeBodyPart htmlBP = new MimeBodyPart();
					htmlBP.setDataHandler(new DataHandler(mail.getByteArrayDataSource()));
					htmlMP.addBodyPart(htmlBP);

					// Add images
					ArrayList images = mail.getHtmlImageDataSources();
					for (int i = 0; i < images.size(); i++)
					{
						DataSource imageDS = (DataSource) images.get(i);

						MimeBodyPart imageBodyPart = new MimeBodyPart();
						imageBodyPart.setFileName(imageDS.getName());
						imageBodyPart.setText(imageDS.getName());
						imageBodyPart.setDataHandler(new DataHandler(imageDS));
						imageBodyPart.setHeader("Content-ID", "<" + imageDS.getName() + ">");
						imageBodyPart.setDisposition(javax.mail.Part.INLINE);

						htmlMP.addBodyPart(imageBodyPart);
					}
				
					BodyPart completeHtmlBP = new MimeBodyPart();
					completeHtmlBP.setContent(htmlMP);
					multipart.addBodyPart(completeHtmlBP);
				}
				else
				{
					MimeBodyPart mbpAttachment = new MimeBodyPart();
					mbpAttachment.setDataHandler(new DataHandler(mail.getByteArrayDataSource()));
					mbpAttachment.setFileName(mail.getByteArrayDataSource().getName());

					multipart.addBodyPart(mbpAttachment);
				}
			}

			// create message
			Message msg = new MimeMessage(mailSession);
			msg.setFrom(from);
			msg.setRecipients(Message.RecipientType.TO, to);
			msg.setSubject(mail.getSubject());
			msg.setContent(multipart);

			Transport.send(msg);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			log.error(e.toString());
			throw new ProviderException(e.getMessage());
		}
	}
	
	public void setMailHost(String mailHost)
	{
		this.mailHost = mailHost;
	}

	public void setPassword(String password)
	{
		this.password = password;
	}

	public void setUseMailAuthenticator(boolean useMailAuthenticator)
	{
		this.useMailAuthenticator = useMailAuthenticator;
	}

	public void setUserName(String userName)
	{
		this.userName = userName;
	}

	public void setPropertiesProvider(PropertiesProvider propertiesProvider)
	{
		this.propertiesProvider = propertiesProvider;
	}

}