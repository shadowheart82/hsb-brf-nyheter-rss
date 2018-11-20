/**
 * 
 */
package se.shadowheart.hsb.brf.news;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;
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
 * @version 1.1
 */
@WebServlet(name = "NewsFeedServlet", urlPatterns = { "/nyheter/*" })
public class NewsFeedServlet extends HttpServlet {

	private static final long serialVersionUID = 5086217682394439582L;
	private static final long minRefreshInterval = 60 * 1000; // 1 minute
	private static final TransformerFactory transformerFactory = TransformerFactory.newInstance();

	private Map<String, Long> lastRefreshes = new HashMap<>();
	private Map<String, NewsFeed> lastNewsFeeds = new HashMap<>();

	private ScheduledExecutorService service;
	private boolean dirty;

	@Override
	public void init() throws ServletException {
		try {
			log("Loading cached feeds...");
			loadCachedNewsFeeds();
		} catch (IOException e) {
			throw new ServletException("Failed to load cached feeds", e);
		}

		service = Executors.newSingleThreadScheduledExecutor();
		service.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				if (dirty) {
					try {
						log("Saving cached feeds...");
						saveCachedNewsFeeds();
					} catch (IOException e) {
						log("Failed to save cached feeds", e);
					}
				}
			}
		}, 1, 1, TimeUnit.MINUTES);
	}

	@Override
	public void destroy() {
		service.shutdownNow();

		if (dirty) {
			try {
				log("Saving cached feeds...");
				saveCachedNewsFeeds();
			} catch (IOException e) {
				log("Failed to save cached feeds", e);
			}
		}
	}

	private void saveCachedNewsFeeds() throws IOException {
		synchronized (lastNewsFeeds) {
			if (dirty) {
				File f = getLastNewsFeedSerFile();

				try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) {
					out.writeObject(lastNewsFeeds);
					log("Successfully saved cached feeds");
				} catch (ObjectStreamException e) {
					f.delete();
					log("Failed to save cached feeds", e);
				} catch (IOException e) {
					f.delete();
					throw e;
				}

				dirty = false;
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void loadCachedNewsFeeds() throws IOException {
		synchronized (lastNewsFeeds) {
			File f = getLastNewsFeedSerFile();

			if (f.isFile()) {
				try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(f)))) {
					lastNewsFeeds = (Map<String, NewsFeed>) in.readObject();
					log("Successfully loaded cached feeds");
				} catch (ClassNotFoundException | ObjectStreamException | ClassCastException e) {
					f.delete();
					log("Failed to load cached feeds", e);
				} catch (IOException e) {
					throw e;
				}
			} else {
				log("No cached feeds found");
			}

			dirty = false;
		}
	}

	private File getLastNewsFeedSerFile() {
		return new File(getSerDir(), getClass().getName() + ".lastNewsFeeds.ser");
	}

	private File getSerDir() {
		Object tempDir = getServletContext().getAttribute(ServletContext.TEMPDIR);

		if (tempDir instanceof File) {
			return (File) tempDir;
		} else if (tempDir != null) {
			return new File(tempDir.toString());
		} else {
			return new File(System.getProperty("java.io.tmpdir"));
		}
	}

	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String[] uriParts = StringUtils.split(StringUtils.strip(req.getPathInfo(), "/"), "/");
		String uri;
		long timeNow = System.currentTimeMillis();
		Long lastRefresh;
		NewsFeed newsFeed;

		if (uriParts == null || uriParts.length == 0) {
			uri = "";
		} else if (uriParts.length == 1) {
			uri = uriParts[0];
		} else if (uriParts.length == 2) {
			uri = uriParts[0] + "/" + uriParts[1];
		} else {
			uri = null;
		}

		lastRefresh = lastRefreshes.get(uri);
		newsFeed = lastNewsFeeds.get(uri);

		try {
			if (lastRefresh != null && timeNow < lastRefresh.longValue() + minRefreshInterval) {
				// Do not refresh
				log("Using feed cached for \"" + uri + "\" @ " + new Date(lastRefresh.longValue()) + "...");
			} else if (uriParts == null || uriParts.length == 0) {
				newsFeed = new NewsFeed(newsFeed);
			} else if (uriParts.length == 1) {
				newsFeed = new NewsFeed(newsFeed, uriParts[0]);
			} else if (uriParts.length == 2) {
				newsFeed = new NewsFeed(newsFeed, uriParts[0], uriParts[1]);
			} else {
				newsFeed = null;
			}
		} catch (HttpStatusException e) {
			log("Failed to parse news feed for \"" + uri + "\": " + e.getMessage(), e);
			resp.sendError(e.getStatusCode(), "Error loading news");
			return;
		} catch (RuntimeException | IOException e) {
			log("Failed to parse news feed for \"" + uri + "\": " + e.getMessage(), e);
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error loading news");
			return;
		}

		if (newsFeed == null) {
			log("Invalid request URI: " + req.getPathInfo());
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Not found");
			return;
		}

		if (lastRefresh == null || timeNow >= lastRefresh.longValue() + minRefreshInterval) {
			log("Caching feed for \"" + uri + "\" @ " + new Date(timeNow) + "...");
			synchronized (lastNewsFeeds) {
				if (!newsFeed.equals(lastNewsFeeds.put(uri, newsFeed))) {
					dirty = true;
				}

				lastRefreshes.put(uri, timeNow);
			}
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
		/*
		NewsFeed newsFeed;

		newsFeed = new NewsFeed("stockholm");
		outputDocument(newsFeed.getDocument(), new StreamResult(System.out), "UTF-8");
		System.out.println();

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
		}

		newsFeed = new NewsFeed(newsFeed, "stockholm");
		outputDocument(newsFeed.getDocument(), new StreamResult(System.out), "UTF-8");
		*/

		outputDocument(new NewsFeed("norr", "hagern").getDocument(), new StreamResult(System.out), "UTF-8");
	}

}
