package com.example.server;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.example.cartdemo.Cart;
import com.example.cartdemo.CartDelta;
import com.example.cartdemo.CartEntry;
import com.example.cartdemo.CurrencyData;
import com.example.cartdemo.Location;
import com.example.cartdemo.Marks;
import com.example.cartdemo.Ping;
import com.example.cartdemo.Price;
import com.example.cartdemo.Stocked;
import com.example.cartdemo.StockedStatus;
import com.example.cartdemo.UnknownStockedStatus;
import static com.example.cartdemo.CartdemoGlobal.diffCart;
import static com.example.cartdemo.CartdemoGlobal.later;
import static com.example.cartdemo.CartdemoGlobal.mergeCart;
import static com.example.cartdemo.CartdemoGlobal.unknownCurrencyData;
import static com.example.cartdemo.CartdemoGlobal.currencyDataTable;

import temper.std.json.JsonAdapter;
import temper.std.json.JsonTextProducer;
import temper.std.json.NullInterchangeContext;
import static temper.std.json.JsonGlobal.parseJson;
import org.apache.log4j.BasicConfigurator;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;

public class RidiculouslySimpleShoppingCartServer extends Handler.Abstract {

  private static Cart emptyCart = new Cart(Collections.emptyList(), new Location(null, new Marks(0, null)));
  private Cart cart = emptyCart;
  private int sMark = 0;

  void serveCartDemoHtml(Request request, Response response) throws IOException {
    response.setStatus(200);
    response.getHeaders().put("Content-type", "text/html");

    // Initialize the single page app (SPA) with the current JSON
    // JavaScript will send/receive adjustments to us.
    int now = now();
    Cart cart;
    int sMark;
    synchronized (this) {
      cart = this.cart;
      sMark = this.sMark;
    }
    CartDelta cartDelta = diffCart(cart, emptyCart, now);
    JsonTextProducer cartJson = new JsonTextProducer();
    CartDelta.jsonAdapter().encodeToJson(cartDelta, cartJson);

    try (
      OutputStream os = Response.asBufferedOutputStream(request, response);
    ) {
      Writer w = new OutputStreamWriter(os, "UTF-8");
      w.write("<!doctype html><meta charset-UTF-8>\n");
      w.write("<title>Cart Demo</title>\n");
      w.write("<style>body { font-family: monospace }</style>\n");
      w.write("<marquee scrollamount=18><h1>This page left intentionally unstyled</h1></marquee>\n");
      // Import some JS that creates an interactive form and
      // syncs the cart with the server.
      // Start with an importmap that includes Temper translated into JS.
      w.write("<script type=importmap>\n");
      w.write(IMPORT_MAP_JSON);
      w.write("</script>\n");
      w.write("<script type=module src=/assets/js.js></script>\n");
      w.write("<script type=module>\n");
      w.write("  import { applyCartDelta } from '/assets/js.js'; \n");
      w.write(String.format(
              "  applyCartDelta(%1$s, %2$d);\n",
              jsEsc(cartJson.toJsonString()), sMark));
      w.write("</script>\n");
      // If JS works, it takes control and hides inputs related to
      // graceful degradation bits.
      w.write("<script>\n");
      w.write("  document.write('<style>.degradegracefully { display: none }</style>');\n");
      w.write("  document.write('<table id=cart-table></table>');\n");
      w.write("</script>\n");
      // Now, if JS isn't running, we'll need a fallback web form.
      w.write("<form class=degradegracefully method=post target=/update-cart>\n");
      w.write(String.format(
              "  <input type=hidden name=initial value='%1$s'>\n",
              htmlEsc(cartJson.toJsonString())));
      w.write(String.format(
              "  <p>Postal Code: <input name=postalCode value='%1$s'></p>\n",
              htmlEsc(or(cart.loc.getPostalCode(), ""))));
      w.write("<table><tr><th>Sku<th>Count</tr>\n");
      List<CartEntry> formEntries = new ArrayList<>(cart.getEntries());
      formEntries.add(new CartEntry("", 1, null, new Marks(0, null))); // Leave room for a new entry
      for (CartEntry e : formEntries) {
        w.write(String.format(
              "  <tr><td><input name=sku value='%1$s'><td input name=count value='%2$d'></tr>\n",
              htmlEsc(e.getSku()),
              e.getCount()));
      }
      w.write("</table>\n");
      w.write("<input type=submit></form>\n");
      w.write("</html>\n");
      w.flush();
    }
  }

  void doUpdateCart(Request request, Response response) throws IOException {
    String contentType = request.getHeaders().get(HttpHeader.CONTENT_TYPE);
    if (contentType != null) {
      contentType = contentType.toLowerCase(Locale.ROOT);
    }
    byte[] bytes = new byte[1024];
    ByteArrayOutputStream bytesAccumulated = new ByteArrayOutputStream();
    try (InputStream in = Request.asInputStream(request)) {
      while (true) {
        int n = in.read(bytes);
        if (n <= 0) { break; }
        bytesAccumulated.write(bytes, 0, n);
      }
    }
    System.err.println("Update got " + contentType + ", " + bytesAccumulated.size() + "B");

    if (contentType.startsWith("application/json")) {
      // Expect a CartDelta as JSON
      String json = bytesAccumulated.toString(StandardCharsets.UTF_8);
      System.err.println("Update got `" + json + "`");
      Ping ping = Ping.jsonAdapter().decodeFromJson(
        parseJson(json),
        NullInterchangeContext.instance
      );

      // Process the ping
      CartDelta change = ping.cartDelta;
      Cart before, after;
      synchronized (this) {
        int sMark = ++this.sMark;
        before = this.cart;
        Location loc = later(before.loc, change.loc);
        after = mergeCart(
           before, change, sMark,
           RidiculouslySimpleShoppingCartServer::stockedForSku
        );
        this.cart = after;
      }

      // Find the result to send back
      Ping pingBack = new Ping(
        diffCart(after, before, ping.sMark),
        sMark
      );

      JsonTextProducer pingBackJson = new JsonTextProducer();
      Ping.jsonAdapter().encodeToJson(pingBack, pingBackJson);
      System.err.println("Update formatted pingBack " + pingBackJson.toJsonString());
      response.getHeaders().put(HttpHeader.CONTENT_TYPE, "application/json");
      try (
        OutputStream out = Response.asBufferedOutputStream(request, response);
      ) {
        Writer w = new OutputStreamWriter(out, "UTF-8");
        w.write(pingBackJson.toJsonString());
        w.flush();
      }
    } else {
      throw new IOException(contentType);
    }
  }

  @Override
  public boolean handle​(
    Request request,
    Response response,
    Callback callback
  ) {
    // Is the request one we want to answer.
    String path = request.getHttpURI().getPath();
    String method = request.getMethod();
    boolean isGet; // false -> POST
    switch (method) {
    case "GET": case "HEAD": isGet = true; break;
    case "POST": isGet = false; break;
    default: return false;
    }

    Throwable problem = null;
    try {
      if (path.startsWith("/assets/") && !path.contains("..") && isGet) {
        try (
          InputStream in = getClass().getResourceAsStream(path);
          OutputStream os = Response.asBufferedOutputStream(request, response)
        ) {
          if (in == null) {
            throw new FileNotFoundException(path);
          }
          response.setStatus(200);
          if (path.endsWith(".js")) {
            response.getHeaders().put("Content-type", "text/javascript");
          }
          setCacheHeaders(response);
          byte[] bytes = new byte[1024];
          while (true) {
            int n = in.read(bytes);
            if (n <= 0) { break; }
            os.write(bytes, 0, n);
          }
        }
      } else if ("/cart-demo".equals(path) && isGet) {
        setCacheHeaders(response);
        serveCartDemoHtml(request, response);
      } else if ("/update-cart".equals(path) && !isGet) {
        doUpdateCart(request, response);
      } else {
        return false;
      }
    } catch (IOException ex) {
      problem = ex;
    } catch (RuntimeException ex) {
      problem = ex;
    }

    if (problem != null) {
      problem.printStackTrace();
      callback.failed(problem);
    } else {
      callback.succeeded();
    }
    return true;
  }

  private String eTag = Long.toString(System.currentTimeMillis(), 16);
  private void setCacheHeaders(Response response) {
    // Associate the supporting assets and the initial page load to
    // a particular version of the server.
    response.getHeaders().put("Cache-control", "must-revalidate");
    response.getHeaders().put("ETag", eTag);
  }

  public static void main(String[] argv) throws Exception {
    // BasicConfigurator.configure(); // Makes logging spammier

    Server server = new Server();
    server.setHandler(new RidiculouslySimpleShoppingCartServer());

    ServerConnector connector = new ServerConnector(server);
    connector.setPort(8090);
    server.setConnectors(new Connector[] { connector });
    server.start();
  }

  private static String htmlEsc(String plainText) {
    StringBuilder sb = new StringBuilder(plainText.length() * 2);
    for (int i = 0, n = plainText.length(); i < n; ++i) {
      char c = plainText.charAt(i);
      String esc =
        switch (c) {
        case '<'  -> "&lt;";
        case '>'  -> "&gt;";
        case '&'  -> "&amp;";
        case '"'  -> "&#34;";
        case '\'' -> "&#39;";
        default   -> null;
        };
      if (esc == null) {
        sb.append(c);
      } else {
        sb.append(esc);
      }
    }
    return sb.toString();
  }

  private static String jsEsc(String plainText) {
    StringBuilder sb = new StringBuilder((plainText.length() + 1) * 2);
    sb.append('\'');
    for (int i = 0, n = plainText.length(); i < n; ++i) {
      char c = plainText.charAt(i);
      String esc =
        switch (c) {
        case '<'      -> "\\x3c";
        case '>'      -> "\\x3e";
        case '&'      -> "\\x26";
        case '"'      -> "\\\"";
        case '\''     -> "\\\'";
        case '\\'     -> "\\\\";
        case '\r'     -> "\\r";
        case '\n'     -> "\\n";
        case '\u2028' -> "\\u2028";
        case '\u2029' -> "\\u2029";
        case '\u0085' -> "\\x85";
        default       -> null;
        };
      if (esc == null) {
        sb.append(c);
      } else {
        sb.append(esc);
      }
    }
    sb.append('\'');
    return sb.toString();
  }

  private static final String IMPORT_MAP_JSON = """
    {
      "imports": {
        "@temperlang/core/": "/assets/temper-core/",
        "@temperlang/core": "/assets/temper-core/index.js",
        "@temperlang/std/": "/assets/std/",
        "@temperlang/std": "/assets/std/index.js",
        "@temperlang/std/json": "/assets/std/json.js",
        "cart-demo/": "/assets/cart-demo/",
        "cart-demo": "/assets/cart-demo/index.js"
      }
    }
    """;

  private static <T> T or(T a, T b) {
    return a != null ? a : b;
  }

  private static int now() {
    return (int) System.currentTimeMillis();
  }

  private static StockedStatus stockedForSku(
    String sku,
    int count,
    Location loc
  ) {
    if (sku == null || sku.length() <= 1) {
      return new UnknownStockedStatus();
    }
    String currencyCode = loc.getCurrencyCode();
    if (currencyCode.equals(unknownCurrencyData.getCode())) {
      return new UnknownStockedStatus();
    }
    CurrencyData currencyData = currencyDataTable.get(currencyCode);
    int hashCode = sku.hashCode();
    boolean available = count == 0 || (hashCode & 0x7) != 0;
    int amount = ((hashCode >> 3) ^ (currencyCode.hashCode() >> 8)) & 0x31;
    for (int i = currencyData.minorUnit; --i >= 0;) {
      amount *= 10;
    }
    Price price = new Price(currencyCode, amount);
    Marks marks = new Marks(0, null);
    return new Stocked(price, available, marks);
  }

  private static <T> String jsonOf(T x, JsonAdapter<T> a) {
    JsonTextProducer out = new JsonTextProducer();
    a.encodeToJson(x, out);
    return out.toJsonString();
  }
}
