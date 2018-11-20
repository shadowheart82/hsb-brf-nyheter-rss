/**
 * 
 */
package se.shadowheart.hsb.brf.news;

import java.io.Serializable;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.time.FastDateFormat;

/**
 * <p>
 * Denna klass representerar en nyhet i ett nyhetsflöde i RSS 2.0-format för en
 * bostadsrättsförening hos HSB.
 * </p>
 * 
 * @author Mikael Lindberg (shadowheart82 / mlindberg82@gmail.com)
 * @version 1.0
 */
public class NewsFeedItem implements Serializable {

	private static final long serialVersionUID = -5366133004525746453L;
	private static final FastDateFormat dateFormatGUID = FastDateFormat.getInstance("yyyyMMddHHmmss", new Locale("en"));

	private final String title;
	private final String link;
	private final String guid;
	private final Date date;
	private final String desc;

	public NewsFeedItem(String title, String link, Date date, String desc) {
		super();
		this.title = title;
		this.link = link;
		this.date = date;
		this.desc = desc;

		if (link == null) {
			this.guid = null;
		} else if (date != null) {
			this.guid = link + "#" + dateFormatGUID.format(date);
		} else {
			this.guid = link;
		}
	}

	public String getTitle() {
		return title;
	}

	public String getLink() {
		return link;
	}

	public String getGuid() {
		return guid;
	}

	public Date getDate() {
		return date;
	}

	public String getDesc() {
		return desc;
	}

	public org.w3c.dom.Element addToChannel(org.w3c.dom.Node channel) {
		org.w3c.dom.Document d = channel.getOwnerDocument();
		org.w3c.dom.Element item = d.createElement("item");

		NewsFeedUtils.addTextChildElement(item, "title", title);
		NewsFeedUtils.addTextChildElement(item, "link", link);
		NewsFeedUtils.addTextChildElement(item, "description", StringEscapeUtils.escapeHtml4(desc));

		if (guid != null) {
			NewsFeedUtils.addTextChildElement(item, "guid", guid);
		}

		if (date != null) {
			NewsFeedUtils.addDateTimeChildElement(item, "pubDate", date);
		}

		channel.appendChild(item);

		return item;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((date == null) ? 0 : date.hashCode());
		result = prime * result + ((desc == null) ? 0 : desc.hashCode());
		result = prime * result + ((link == null) ? 0 : link.hashCode());
		result = prime * result + ((title == null) ? 0 : title.hashCode());
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
		NewsFeedItem other = (NewsFeedItem) obj;
		if (desc == null) {
			if (other.desc != null)
				return false;
		} else if (!desc.equals(other.desc))
			return false;
		if (link == null) {
			if (other.link != null)
				return false;
		} else if (!link.equals(other.link))
			return false;
		if (title == null) {
			if (other.title != null)
				return false;
		} else if (!title.equals(other.title))
			return false;
		return true;
	}

}
