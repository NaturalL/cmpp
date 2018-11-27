package org.ne81.sp.cmpp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.simplejavamail.email.Email;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;
import org.simplejavamail.mailer.config.TransportStrategy;

@SuppressWarnings("ALL")
public class CmppUtil {

	static String emailRegex = "[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$";
	static Pattern emailPattern = Pattern.compile(emailRegex);

	static Mailer mailer = MailerBuilder
			.withSMTPServer("", 0, "", "")
			.withTransportStrategy(TransportStrategy.SMTPS)
			.withSessionTimeout(10 * 1000)
			.clearEmailAddressCriteria() // turns off email validation
			.withDebugLogging(true)
			.buildMailer();

	static ObjectMapper objectMapper = new ObjectMapper();


	public static List<String> extractEmails(String s) {
		List<String> emails = new ArrayList<String>();
		Matcher m = emailPattern.matcher(s);
		while (m.find()) {
			emails.add(m.group());
		}
		return emails;
	}

	public static String toJson(Object o) {
		String s = null;
		try {
			s = objectMapper.writeValueAsString(o);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return s;
	}


	public static <T> T fromJson(String json,  Class<T> valueType) {
		if(json == null) {
			return null;
		}
		try {
			T o = objectMapper.readValue(json, valueType);
			return o;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void email(String title, String content, List<String> addrs) {
		try {
			Email email = EmailBuilder.startingBlank()
					.from("", "")
					.to("", addrs)
					.withSubject("【cmpp测试通道】" + title)
					.withPlainText(content)
					.buildEmail();
			mailer.sendMail(email);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void main(String[] args) {
	}

	public static byte[] getLenBytes(String s, int len) {
		if (s == null) {
			s = "";
		}
		byte[] rb = new byte[len];
		byte[] sb = s.getBytes();
		for (int i = sb.length; i < rb.length; i++) {
			rb[i] = 0;
		}
		if (sb.length == len) {
			return sb;
		} else {
			for (int i = 0; i < sb.length && i < len; i++) {
				rb[i] = sb[i];
			}
			return rb;
		}
	}

	public static byte[] getBytes(byte[] inBytes, int len) {
		byte[] outBytes = new byte[len];
		if (inBytes != null) {
			for (int i = 0; i < inBytes.length && i < len; i++) {
				outBytes[i] = inBytes[i];
			}
		}
		return outBytes;
	}

	public static String getStringFromBuffer(java.nio.ByteBuffer buf, int len) {
		try {
			byte[] bytes = new byte[len];
			buf.get(bytes);
			return esc0(new String(bytes));
		} catch (Exception e) {
			e.printStackTrace();
			return "[getStringFromBuffer error]";
		}

	}

	public static String esc0(String s) {
		if (s == null || s.length() == 0) {
			s = "";
			return s;
		} else {
			int i = s.indexOf('\0');
			if (i > 0)
				s = s.substring(0, i);
			else
				s = s.replaceAll("\0", "");
		}
		return s;
	}

	public static byte[] getMessageContentBytes(String msgContent, byte msgFmt) {
		if (msgFmt == (byte) 8) {
			try {
				return msgContent.getBytes("UnicodeBigUnmarked");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		} else if (msgFmt == (byte) 15) {
			try {
				return msgContent.getBytes("gbk");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		return msgContent.getBytes();

	}

	public static String getMessageContent(byte[] msgContentBytes, byte msgFmt) {
		return getMessageContent(msgContentBytes, msgFmt, (byte)1);
	}

	public static String getMessageContent(byte[] msgContentBytes, byte msgFmt, byte tp_udhi) {
		if (msgContentBytes == null)
			return "";
		String msgContent = "";
		int offset = 0;
		if (tp_udhi == (byte)1 && msgContentBytes.length > 1 && msgContentBytes[0] == 0x06) {
			offset = 7;
		}
		if (tp_udhi == (byte)1 && msgContentBytes.length > 1 && msgContentBytes[0] == 0x05) {
			offset = 6;
		}
		if (msgFmt == (byte) 8) {
			try {
				msgContent = new String(msgContentBytes, offset, msgContentBytes.length - offset,
						"UnicodeBigUnmarked");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		} else if (msgFmt == (byte) 15) {
			try {
				msgContent = new String(msgContentBytes, offset, msgContentBytes.length - offset,
						"gbk");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		} else {
			msgContent = new String(msgContentBytes);
		}
		return msgContent;
	}

	public static String[] splitString(String in, int len) {
		int totalSegments = (int) Math.ceil(in.length() / (double) len);
		String splittedMsg[] = new String[totalSegments];
		for (int j = 0; j < totalSegments; j++) {
			int endIndex = (j + 1) * len;
			if (endIndex > in.length()) {
				endIndex = in.length();
			}
			splittedMsg[j] = in.substring(j * len, endIndex);
		}
		return splittedMsg;
	}

	public static CmppSubmit[] getConcatenatedSms(CmppSubmit submit, String shortMessage) {
		if (shortMessage.length() <= 70) {
			try {
				if (submit.getMsgFmt() == (byte) 8)
					submit.setMsgContent(shortMessage.getBytes("UnicodeBigUnmarked"));
				else
					submit.setMsgContent(shortMessage.getBytes("gbk"));
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();// 应该不会报错
			}
			return new CmppSubmit[] { submit };
		}

		String splittedMsg[] = splitString(shortMessage, 67);
		int totalSegments = splittedMsg.length;

		submit.setTp_udhi((byte) 0x01);
		// iteerating on splittedMsg array. Only Sequence Number and short
		// message text will change each time
		CmppSubmit submits[] = new CmppSubmit[totalSegments];
		for (int i = 0; i < totalSegments; i++) {
			byte[] msg = null;
			try {
				if (submit.getMsgFmt() == (byte) 8)
					msg = splittedMsg[i].getBytes("UnicodeBigUnmarked");
				else
					msg = splittedMsg[i].getBytes("gbk");
			} catch (UnsupportedEncodingException e1) {
				// 不会出问题
				e1.printStackTrace();
			}

			ByteBuffer ed = ByteBuffer.allocate(6 + msg.length);

			ed.put((byte) 5); // UDH Length

			ed.put((byte) 0); // IE Identifier

			ed.put((byte) 3); // IE Data Length

			ed.put((byte) 0); // Reference Number
			ed.put((byte) totalSegments); // Number of pieces

			ed.put((byte) (i + 1)); // Sequence number

			// This encoding comes in Logica Open SMPP. Refer to its docs for
			// more detail
			ed.put(msg);
			try {
				submits[i] = submit.clone();
			} catch (CloneNotSupportedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (i > 0)
				submits[i].setSequenceId(Sequence.getInstance().getSequence());
			submits[i].setMsgContent(ed.array());
		}
		return submits;
	}

	public static CmppDeliver[] getConcatenatedUpSms(CmppDeliver deliver, String shortMessage) {
		if (shortMessage.length() <= 70) {
			try {
				if (deliver.getMsgFmt() == (byte) 8)
					deliver.setMsgContent(shortMessage.getBytes("UnicodeBigUnmarked"));
				else
					deliver.setMsgContent(shortMessage.getBytes("gbk"));
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();// 应该不会报错
			}
			return new CmppDeliver[] { deliver };
		}

		String splittedMsg[] = splitString(shortMessage, 67);
		int totalSegments = splittedMsg.length;

		deliver.setTp_udhi((byte) 0x01);
		// iteerating on splittedMsg array. Only Sequence Number and short
		// message text will change each time
		CmppDeliver delivers[] = new CmppDeliver[totalSegments];
		for (int i = 0; i < totalSegments; i++) {
			byte[] msg = null;
			try {
				if (deliver.getMsgFmt() == (byte) 8)
					msg = splittedMsg[i].getBytes("UnicodeBigUnmarked");
				else
					msg = splittedMsg[i].getBytes("gbk");
			} catch (UnsupportedEncodingException e1) {
				// 不会出问题
				e1.printStackTrace();
			}

			ByteBuffer ed = ByteBuffer.allocate(6 + msg.length);

			ed.put((byte) 5); // UDH Length

			ed.put((byte) 0); // IE Identifier

			ed.put((byte) 3); // IE Data Length

			ed.put((byte) 0); // Reference Number
			ed.put((byte) totalSegments); // Number of pieces

			ed.put((byte) (i + 1)); // Sequence number

			// This encoding comes in Logica Open SMPP. Refer to its docs for
			// more detail
			ed.put(msg);
			try {
				delivers[i] = deliver.clone();
			} catch (CloneNotSupportedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (i > 0)
				delivers[i].setSequenceId(Sequence.getInstance().getSequence());
			delivers[i].setMsgContent(ed.array());
		}
		return delivers;
	}

	/**
	 * Load a given resource.
	 * <p/>
	 * This method will try to load the resource using the following methods (in
	 * order):
	 * <ul>
	 * <li>From {@link Thread#getContextClassLoader()
	 * Thread.currentThread().getContextClassLoader()}
	 * <li>From {@link Class#getClassLoader()
	 * ClassLoaderUtil.class.getClassLoader()}
	 * <li>From the {@link Class#getClassLoader() callingClass.getClassLoader()
	 * * }
	 * </ul>
	 *
	 * @param resourceName
	 *            The name of the resource to load
	 * @param callingClass
	 *            The Class object of the calling object
	 */
	public static URL getResource(String resourceName, Class<?> callingClass) {
		URL url = Thread.currentThread().getContextClassLoader().getResource(resourceName);

		if (url == null) {
			url = CmppUtil.class.getClassLoader().getResource(resourceName);
		}

		if (url == null) {
			ClassLoader cl = callingClass.getClassLoader();

			if (cl != null) {
				url = cl.getResource(resourceName);
			}
		}

		if ((url == null) && (resourceName != null)
				&& ((resourceName.length() == 0) || (resourceName.charAt(0) != '/'))) {
			return getResource('/' + resourceName, callingClass);
		}

		return url;
	}

	public static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}
}
