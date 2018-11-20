/**
 * 
 */
package se.shadowheart.hsb.brf.news;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.FastDateFormat;
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
 * @version 1.1
 */
public class NewsFeed implements Serializable {

	private static final long serialVersionUID = -869139278667191291L;
	private static final FastDateFormat dateFormatIn = FastDateFormat.getInstance("dd MMMM yyyy", new Locale("sv"));

	public static final String URL_PATTERN_0 = "https://www.hsb.se/nyheter";
	public static final String URL_PATTERN_1 = "https://www.hsb.se/%1$s/om-hsb/nyheter";
	public static final String URL_PATTERN_2 = "http://www.hsb.se/%1$s/brf/%2$s/nyheter";
	public static final String USER_AGENT = "Mozilla";

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

	private final String url;
	private final String title;
	private final String description;
	private final Date lastBuildDate;
	private final List<NewsFeedItem> items = new ArrayList<>();

	public NewsFeed() throws IOException {
		this(null, createURL());
	}

	public NewsFeed(String region) throws IOException {
		this(null, createURL(region));
	}

	public NewsFeed(String region, String brf) throws IOException {
		this(null, createURL(region, brf));
	}

	public NewsFeed(NewsFeed prev) throws IOException {
		this(prev, createURL());
	}

	public NewsFeed(NewsFeed prev, String region) throws IOException {
		this(prev, createURL(region));
	}

	public NewsFeed(NewsFeed prev, String region, String brf) throws IOException {
		this(prev, createURL(region, brf));
	}

	public NewsFeed(String region, String brf, Throwable t) {
		this(region, brf, t.getLocalizedMessage(), getStackTrace(t));
	}

	private NewsFeed(String region, String brf, String title, String description) {
		this(toString(createURL(region, brf)), title, description);
	}

	private NewsFeed(String url, String title, String description) {
		super();
		this.url = url;
		this.title = title;
		this.description = description;
		this.lastBuildDate = new Date();
		items.add(new NewsFeedItem(title, null, lastBuildDate, description));
	}

	private NewsFeed(NewsFeed prev, URL url) throws IOException {
		super();
		Document document = Jsoup.connect(toString(url)).userAgent(USER_AGENT).get();
		String title = document.select("div.brf-header-bottom-text > span").text();
		Date maxDate = null;

		if (title == null || title.isEmpty()) {
			title = document.select("div.regionname").text();
		}

		for (Element item : document.select("ul.itemlist > li.item")) {
			maxDate = maxDate(maxDate, addItem(prev, url, item.select("a.linkclickarea").first()));
		}

		this.url = toString(url);
		this.title = title;
		this.description = "";
		this.lastBuildDate = maxDate;
	}

	private Date addItem(NewsFeed prev, URL url, Element linkclickarea) throws MalformedURLException {
		Element iteminformation = select(linkclickarea, "div.iteminformation").first();
		String title = selectFirstText(iteminformation, "h3");
		String link = attr(linkclickarea, "href");
		Date date;
		String description = selectFirstText(iteminformation, "div.itemdescription");
		NewsFeedItem newItem;
		NewsFeedItem prevItem = null;
		long now = System.currentTimeMillis();

		try {
			date = dateFormatIn.parse(selectFirstText(iteminformation, "div.itemdate"));
		} catch (ParseException | RuntimeException e) {
			date = null;
		}

		if (url != null) {
			newItem = new NewsFeedItem(title, toString(new URL(url, link)), date, description);
		} else {
			newItem = new NewsFeedItem(title, null, date, description);
		}

		if (prev != null && link != null) {
			for (NewsFeedItem other : prev.items) {
				if (newItem.getLink().equals(other.getLink())) {
					prevItem = other;
					break;
				}
			}
		}

		if (date != null && DateUtils.isSameDay(new Date(now), date)) {
			if (prevItem == null || !prevItem.equals(newItem)) {
				newItem.getDate().setTime(now);
			} else if (prevItem.getDate() != null) {
				newItem.getDate().setTime(prevItem.getDate().getTime());
			}
		}

		items.add(newItem);

		return date;
	}

	private static URL createURL() {
		try {
			return new URL(URL_PATTERN_0);
		} catch (MalformedURLException e) {
			return null;
		}
	}

	private static URL createURL(String region) {
		try {
			return new URL(String.format(URL_PATTERN_1, encodeForURL(region)));
		} catch (MalformedURLException e) {
			return null;
		}
	}

	private static URL createURL(String region, String brf) {
		try {
			return new URL(String.format(URL_PATTERN_2, encodeForURL(region), encodeForURL(brf)));
		} catch (MalformedURLException e) {
			return null;
		}
	}

	@SuppressWarnings("deprecation")
	public static String encodeForURL(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			return URLEncoder.encode(s);
		}
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

	public org.w3c.dom.Document getDocument() {
		org.w3c.dom.Document rss = createEmptyRss();
		org.w3c.dom.Node channel = addChannel(rss.getDocumentElement(), title, url, description);
		org.w3c.dom.Node refChild = channel.getLastChild();
		org.w3c.dom.Node newChild;

		for (NewsFeedItem item : items) {
			item.addToChannel(channel);
		}

		refChild = refChild.getNextSibling();

		if (lastBuildDate != null) {
			newChild = NewsFeedUtils.addDateTimeChildElement(channel, "lastBuildDate", lastBuildDate);
			channel.insertBefore(newChild, refChild);
		}

		channel.insertBefore(NewsFeedUtils.addTextChildElement(channel, "ttl", "60"), refChild);

		return rss;
	}

	private org.w3c.dom.Document createEmptyRss() {
		org.w3c.dom.Document d = documentBuilder.get().newDocument();
		org.w3c.dom.Element rss = d.createElement("rss");

		rss.setAttribute("version", "2.0");
		d.appendChild(rss);

		return d;
	}

	private org.w3c.dom.Node addChannel(org.w3c.dom.Element rss, String title, String url, String description) {
		org.w3c.dom.Document d = rss.getOwnerDocument();
		org.w3c.dom.Node channel = rss.appendChild(d.createElement("channel"));
		org.w3c.dom.Element image;

		NewsFeedUtils.addTextChildElement(channel, "title", title);
		NewsFeedUtils.addTextChildElement(channel, "link", url);
		NewsFeedUtils.addTextChildElement(channel, "description", description);
		NewsFeedUtils.addTextChildElement(channel, "language", "sv");
		NewsFeedUtils.addTextChildElement(channel, "copyright", "Copyright " + Calendar.getInstance().get(Calendar.YEAR) + ", HSB");

		image = d.createElement("image");
		NewsFeedUtils.addTextChildElement(image, "title", "HSB");
		NewsFeedUtils.addTextChildElement(image, "link", url);
		NewsFeedUtils.addTextChildElement(image, "url", "http://www.hsb.se/globalassets/centralt-innehall/media/logo/hsblogo.png");
		NewsFeedUtils.addTextChildElement(image, "width", "181");
		NewsFeedUtils.addTextChildElement(image, "height", "132");
		channel.appendChild(image);

		return channel;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((description == null) ? 0 : description.hashCode());
		result = prime * result + ((items == null) ? 0 : items.hashCode());
		result = prime * result + ((title == null) ? 0 : title.hashCode());
		result = prime * result + ((url == null) ? 0 : url.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		NewsFeed other = (NewsFeed) obj;
		if (description == null) {
			if (other.description != null)
				return false;
		} else if (!description.equals(other.description))
			return false;
		if (items == null) {
			if (other.items != null)
				return false;
		} else if (!items.equals(other.items))
			return false;
		if (title == null) {
			if (other.title != null)
				return false;
		} else if (!title.equals(other.title))
			return false;
		if (url == null) {
			if (other.url != null)
				return false;
		} else if (!url.equals(other.url))
			return false;
		return true;
	}

	private static Date maxDate(Date date1, Date date2) {
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

	private static String toString(URL url) {
		return (url == null) ? null : url.toString();
	}

	private static String getStackTrace(Throwable t) {
		StringWriter buffer = new StringWriter();

		t.printStackTrace(new PrintWriter(buffer));

		return buffer.toString();
	}

}
