/**
 * 
 */
package se.shadowheart.hsb.brf.news;

import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang3.time.FastDateFormat;

/**
 * <p>
 * Denna klass innehåller diverse hjälpfunktioner.
 * </p>
 * 
 * @author Mikael Lindberg (shadowheart82 / mlindberg82@gmail.com)
 * @version 1.0
 */
public class NewsFeedUtils {

	private static final FastDateFormat dateFormatOut = FastDateFormat.getInstance("E, dd MMM yyyy HH:mm:ss XXX", new Locale("en"));

	public static org.w3c.dom.Element addTextChildElement(org.w3c.dom.Node node, String name, String text) {
		org.w3c.dom.Element child = null;

		if (text != null) {
			child = node.getOwnerDocument().createElement(name);
			node.appendChild(child).setTextContent(text);
		}

		return child;
	}

	public static org.w3c.dom.Element addDateTimeChildElement(org.w3c.dom.Node node, String name, Date dateTime) {
		return addTextChildElement(node, name, dateFormatOut.format(dateTime));
	}

}
