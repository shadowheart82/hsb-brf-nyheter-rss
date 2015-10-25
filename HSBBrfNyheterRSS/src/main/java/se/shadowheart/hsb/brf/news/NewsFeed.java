/**
 * 
 */
package se.shadowheart.hsb.brf.news;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * <p>
 * Denna klass representerar ett nyhetsflöde i RSS 2.0-format för en
 * bostadsrättsförening hos HSB. Ett nyhetsflöde initieras med ett regionsnamn
 * och bostadsrättsnamn.
 * </p>
 * 
 * @author Mikael Lindberg (shadowheart82 / mlindberg82@gmail.com)
 * @version 1.0
 */
public class NewsFeed {

	public static final String URL_PATTERN = "http://www.hsb.se/%1$s/brf/%2$s/nyheter";
	public static final String USER_AGENT = "Mozilla";

	private final SimpleDateFormat dateFormatIn = new SimpleDateFormat("dd MMMM yyyy", new Locale("sv"));
	private final DateFormat dateFormatOut = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss XXX", new Locale("en"));
	private final DateFormat dateFormatGUID = new SimpleDateFormat("yyyy-MM-dd", new Locale("en"));

	private static final ThreadLocal<DocumentBuilder> documentBuilder = new ThreadLocal<DocumentBuilder>() {

		@Override
		protected DocumentBuilder initialValue() {
			DocumentBuilderFactory factory;

			factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);

			try {
				return factory.newDocumentBuilder();
			} catch (ParserConfigurationException e) {
				throw new RuntimeException("Internt fel", e);
			}
		}

	};

	private final org.w3c.dom.Document document;

	public NewsFeed() {
		this("", "FEL: Inga nyheter hittades", "FEL: Inga nyheter hittades");
	}

	public NewsFeed(String region, String brf, Throwable t) throws IOException {
		this(region, brf, t.getLocalizedMessage(), getStackTrace(t));
	}

	private NewsFeed(String region, String brf, String title, String description) throws IOException {
		this(createURL(region, brf).toString(), title, description);
	}

	private NewsFeed(String url, String title, String description) {
		super();
		org.w3c.dom.Document d = createRss();
		org.w3c.dom.Node channel = addChannel(d.getDocumentElement(), title, url, description);

		addItem(channel, title, null, new Date(), description);
		this.document = d;
	}

	public NewsFeed(String region, String brf) throws IOException {
		this(createURL(region, brf));
	}

	private NewsFeed(URL url) throws IOException {
		super();
		Document document = Jsoup.connect(url.toString()).userAgent(USER_AGENT).get();
		String title = document.select("div.brf-header-bottom-text > span").text();
		org.w3c.dom.Document d = createRss();
		org.w3c.dom.Node channel = addChannel(d.getDocumentElement(), title, url.toString(), "");
		org.w3c.dom.Node refChild = channel.getLastChild();
		org.w3c.dom.Node newChild;
		Date lastBuildDate = null;

		for (Element item : document.select("ul.itemlist > li.item")) {
			lastBuildDate = maxDate(lastBuildDate, addItem(channel, url, item.select("a.linkclickarea").first()));
		}

		refChild = refChild.getNextSibling();

		if (lastBuildDate != null) {
			newChild = addTextChildElement(channel, "lastBuildDate", dateFormatOut.format(lastBuildDate));
			channel.insertBefore(newChild, refChild);
		}

		channel.insertBefore(addTextChildElement(channel, "ttl", "60"), refChild);

		this.document = d;
	}

	private Date maxDate(Date date1, Date date2) {
		if (date1 == null) {
			return date2;
		} else if (date2 == null) {
			return date1;
		} else if (date1.compareTo(date2) > 0) {
			return date1;
		} else {
			return date2;
		}
	}

	private org.w3c.dom.Document createRss() {
		org.w3c.dom.Document d = documentBuilder.get().newDocument();
		org.w3c.dom.Element rss = d.createElement("rss");

		rss.setAttribute("version", "2.0");
		d.appendChild(rss);

		return d;
	}

	private org.w3c.dom.Node addChannel(org.w3c.dom.Element rss, String title, String url, String description) {
		org.w3c.dom.Document d = rss.getOwnerDocument();
		org.w3c.dom.Node channel = rss.appendChild(d.createElement("channel"));

		addTextChildElement(channel, "title", title);
		addTextChildElement(channel, "link", url);
		addTextChildElement(channel, "description", description);
		addTextChildElement(channel, "language", "sv");

		return channel;
	}

	private Date addItem(org.w3c.dom.Node channel, URL url, Element linkclickarea) throws MalformedURLException {
		Element iteminformation = select(linkclickarea, "div.iteminformation").first();
		String title = selectFirstText(iteminformation, "h3");
		String link = attr(linkclickarea, "href");
		Date date;
		String description = selectFirstText(iteminformation, "div.itemdescription");

		try {
			date = dateFormatIn.parse(selectFirstText(iteminformation, "div.itemdate"));
		} catch (ParseException | RuntimeException e) {
			date = null;
		}

		addItem(channel, title, new URL(url, link).toString(), date, description);

		return date;
	}

	private org.w3c.dom.Element addItem(org.w3c.dom.Node channel, String title, String link, Date date, String desc) {
		org.w3c.dom.Document d = channel.getOwnerDocument();
		org.w3c.dom.Element item = d.createElement("item");

		addTextChildElement(item, "title", title);
		addTextChildElement(item, "link", link);
		addTextChildElement(item, "description", StringEscapeUtils.escapeHtml4(desc));

		if (link != null) {
			if (date != null) {
				addTextChildElement(item, "guid", link + "#" + dateFormatGUID.format(date));
			} else {
				addTextChildElement(item, "guid", link);
			}
		}

		if (date != null) {
			addTextChildElement(item, "pubDate", dateFormatOut.format(date));
		}

		channel.appendChild(item);

		return item;
	}

	private org.w3c.dom.Element addTextChildElement(org.w3c.dom.Node node, String name, String text) {
		org.w3c.dom.Element child = null;

		if (text != null) {
			child = node.getOwnerDocument().createElement(name);
			node.appendChild(child).setTextContent(text);
		}

		return child;
	}

	private static URL createURL(String region, String brf) throws MalformedURLException, UnsupportedEncodingException {
		return new URL(String.format(URL_PATTERN, URLEncoder.encode(region, "UTF-8"), URLEncoder.encode(brf, "UTF-8")));
	}

	private String attr(Element element, String attributeKey) {
		if (element != null) {
			return element.attr(attributeKey);
		} else {
			return null;
		}
	}

	private Elements select(Element element, String cssQuery) {
		if (element != null) {
			return element.select(cssQuery);
		} else {
			return new Elements();
		}
	}

	private String selectFirstText(Element element, String cssQuery) {
		Element child = select(element, cssQuery).first();

		if (child != null) {
			return child.text();
		} else {
			return null;
		}
	}

	private static String getStackTrace(Throwable t) {
		StringWriter buffer = new StringWriter();

		t.printStackTrace(new PrintWriter(buffer));

		return buffer.toString();
	}

	public org.w3c.dom.Document getDocument() {
		return document;
	}

}
