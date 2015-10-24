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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * <p>
 * Denna klass representerar ett nyhetsflöde i RSS-format för en
 * bostadsrättsförening hos HSB. Ett nyhetsflöde initieras med ett regionsnamn och 
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
		org.w3c.dom.Document d = documentBuilder.get().newDocument();
		org.w3c.dom.Element rss = (org.w3c.dom.Element) d.appendChild(d.createElement("rss"));
		org.w3c.dom.Node channel = rss.appendChild(d.createElement("channel"));

		rss.setAttribute("version", "2.0");
		channel.appendChild(d.createElement("title")).setTextContent(title);
		channel.appendChild(d.createElement("link")).setTextContent(url);
		channel.appendChild(d.createElement("description")).setTextContent(description);
		channel.appendChild(d.createElement("language")).setTextContent("sv");
		addItem(channel, title, url, new Date(), description);

		this.document = d;
	}

	public NewsFeed(String region, String brf) throws IOException {
		this(createURL(region, brf));
	}

	private NewsFeed(URL url) throws IOException {
		super();
		Document document = Jsoup.connect(url.toString()).userAgent(USER_AGENT).get();
		org.w3c.dom.Document d = documentBuilder.get().newDocument();
		org.w3c.dom.Element rss = (org.w3c.dom.Element) d.appendChild(d.createElement("rss"));
		org.w3c.dom.Node channel = rss.appendChild(d.createElement("channel"));
		String title = document.select("div.brf-header-bottom-text > span").text();

		rss.setAttribute("version", "2.0");
		channel.appendChild(d.createElement("title")).setTextContent(title);
		channel.appendChild(d.createElement("link")).setTextContent(url.toString());
		channel.appendChild(d.createElement("description")).setTextContent("");
		channel.appendChild(d.createElement("language")).setTextContent("sv");

		for (Element item : document.select("ul.itemlist > li.item")) {
			addItem(channel, url, item.select("a.linkclickarea").first());
		}

		this.document = d;
	}

	private void addItem(org.w3c.dom.Node channel, URL url, Element linkclickarea) throws MalformedURLException {
		Element iteminformation = linkclickarea.select("div.iteminformation").first();
		String title = iteminformation.select("h3").first().text();
		String link = linkclickarea.attr("href");
		Date date;
		String description = iteminformation.select("div.itemdescription").first().text();

		try {
			date = dateFormatIn.parse(iteminformation.select("div.itemdate").first().text());
		} catch (ParseException | RuntimeException e) {
			date = null;
		}

		addItem(channel, title, new URL(url, link).toString(), date, description);
	}

	private org.w3c.dom.Element addItem(org.w3c.dom.Node channel, String title, String link, Date date, String desc) {
		org.w3c.dom.Document d = channel.getOwnerDocument();
		org.w3c.dom.Element item = d.createElement("item");

		addTextChildElement(item, "title", title);
		addTextChildElement(item, "link", link);
		addTextChildElement(item, "description", desc);

		if (date != null) {
			addTextChildElement(item, "pubDate", dateFormatOut.format(date));
		}

		channel.appendChild(item);

		return item;
	}

	private void addTextChildElement(org.w3c.dom.Element node, String name, String text) {
		if (text != null) {
			node.appendChild(node.getOwnerDocument().createElement(name)).setTextContent(text);
		}
	}

	private static URL createURL(String region, String brf) throws MalformedURLException, UnsupportedEncodingException {
		return new URL(String.format(URL_PATTERN, URLEncoder.encode(region, "UTF-8"), URLEncoder.encode(brf, "UTF-8")));
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
