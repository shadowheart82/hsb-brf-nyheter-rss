/**
 * 
 */
package se.shadowheart.hsb.brf.news;

import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.HttpStatusException;

/**
 * <p>
 * Servlet som läser HTML-innehållet på en nyhetssida för en HSB
 * bostadsrättsförening, och omvandlar till RSS 2.0-format
 * </p>
 * 
 * @author Mikael Lindberg (shadowheart82 / mlindberg82@gmail.com)
 * @version 1.0
 */
@WebServlet(name = "NewsFeedServlet", urlPatterns = { "/nyheter/*" })
public class NewsFeedServlet extends HttpServlet {

	private static final long serialVersionUID = 5086217682394439582L;

	public static final String URL_PATTERN = "http://www.hsb.se/%1/brf/%2/nyheter";
	public static final String USER_AGENT = "Mozilla";

	private static final TransformerFactory transformerFactory = TransformerFactory.newInstance();

	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String[] uriParts = StringUtils.split(StringUtils.strip(req.getPathInfo(), "/"), "/");
		NewsFeed newsFeed;

		if (uriParts != null && uriParts.length == 2) {
			try {
				newsFeed = new NewsFeed(uriParts[0], uriParts[1]);
			} catch (HttpStatusException e) {
				log("Failed to parse news feed: " + e.getMessage(), e);
				resp.sendError(e.getStatusCode(), "Error loading news");
				return;
			} catch (RuntimeException | IOException e) {
				log("Failed to parse news feed: " + e.getMessage(), e);
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error loading news");
				return;
			}
		} else {
			log("Invalid request URI: " + req.getPathInfo());
			newsFeed = new NewsFeed();
		}

		outputDocument(resp, newsFeed.getDocument());
	}

	private static void outputDocument(HttpServletResponse resp, org.w3c.dom.Document d) throws IOException {
		resp.setContentType("application/rss+xml; charset=UTF-8");
		outputDocument(d, resp.getOutputStream(), "UTF-8");
	}

	private static void outputDocument(org.w3c.dom.Document d, OutputStream out, String encoding) throws IOException {
		outputDocument(d, new StreamResult(out), encoding);
	}

	private static void outputDocument(org.w3c.dom.Document d, Result result, String encoding) throws IOException {
		Transformer transformer;

		try {
			transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.ENCODING, encoding);
			transformer.transform(new DOMSource(d), result);
		} catch (TransformerException e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	public static void main(String[] args) throws IOException {
		outputDocument(new NewsFeed("norr", "hagern").getDocument(), new StreamResult(System.out), "UTF-8");
	}

}
