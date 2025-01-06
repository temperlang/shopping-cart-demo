package com.example.server;

import java.io.*;
import java.util.*;
import com.example.cartdemo.Price;
import com.example.cartdemo.Cart;
import com.example.cartdemo.CartEntry;
import static com.example.cartdemo.CartdemoGlobal.diffCart;
import com.example.cartdemo.CartDelta;
import temper.std.json.JsonTextProducer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;

public class RidiculouslySimpleShoppingCartServer extends Handler.Abstract {

  private static Cart emptyCart = new Cart(Collections.emptyList(), null, 0);
  private Cart cart = emptyCart;

  void handleGet(Request request, Response response) throws IOException {
    response.setStatus(200);
    response.getHeaders().put("Content-type", "text/html");

    // Initialize the single page app (SPA) with the current JSON
    // JavaScript will send/receive adjustments to us.
    int now = (int) System.currentTimeMillis();
    Cart cart = this.cart;
    CartDelta cartDelta = diffCart(cart, emptyCart, now);
    JsonTextProducer cartJson = new JsonTextProducer();
    CartDelta.jsonAdapter().encodeToJson(cartDelta, cartJson);

    try (
         OutputStream os = Response.asBufferedOutputStream(request, response);
         Writer w = new OutputStreamWriter(os, "UTF-8")
    ) {
      w.write("<!doctype html><meta charset-UTF-8>\n");
      w.write("<title>Cart Demo</title>\n");
      w.write("<h1>This page left intentionally unstyled</h1>\n");
      // Import some JS that creates an interactive form and
      // syncs the cart with the server.
      // Start with an importmap that includes Temper translated into JS.
      w.write("<script type=importmap>\n");
      w.write(IMPORT_MAP_JSON);
      w.write("</script>\n");
      w.write("<script type=module src=/assets/js.js></script>\n");
      w.write("<script type=module>\n");
      w.write("  import { updateCart } from '/assets/js.js'; \n");
      w.write(String.format(
              "  updateCart(%1$s);\n",
              jsEsc(cartJson.toJsonString())));
      // If JS works, it takes control and hides inputs related to
      // graceful degradation bits.
      w.write("  document.write('<style>.degradegracefully { display: none }</style>');\n");
      w.write("</script>\n");
      // Now, if JS isn't running, we'll need a fallback web form.
      w.write("<form class=degradegracefully method=post target=cart-demo>\n");
      w.write(String.format(
              "  <input type=hidden name=initial value='%1$s'>\n",
              htmlEsc(cartJson.toJsonString())));
      w.write(String.format(
              "  <p>Postal Code: <input name=postalCode value='%1$s'></p>\n",
              htmlEsc(or(cart.getPostalCode(), ""))));
      w.write("<table><tr><th>Sku<th>Count</tr>\n");
      List<CartEntry> entries = new ArrayList<>(cart.getEntries());
      entries.add(new CartEntry("", 1, null, 0)); // Leave room for a new entry
      for (CartEntry e : entries) {
        w.write(String.format(
              "  <tr><td><input name=sku value='%1$s'><td input name=count value='%2$d'></tr>\n",
              htmlEsc(e.getSku()),
              e.getCount()));
      }
      w.write("</table>\n");
      w.write("<input type=submit></form>\n");
      w.write("</html>\n");
    }
  }

  void handlePost(Request request, Response response) {

  }

  @Override
  public boolean handle​(
    Request request,
    Response response,
    Callback callback
  ) {
    // Is the request one we want to answer.
    String path = request.getHttpURI().getPath();
    Throwable problem = null;
    if (path.startsWith("/assets/") && !path.contains("..")) {
      System.err.println("Reading " + path);
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
        byte[] bytes = new byte[1024];
        while (true) {
          int n = in.read(bytes);
          if (n <= 0) { break; }
          os.write(bytes, 0, n);
        }
      } catch (IOException ex) {
        ex.printStackTrace();
        problem = ex;
      }
      System.err.println("Read");
    } else if ("/cart-demo".equals(path)) {
      try {
        switch (request.getMethod()) {
        case "GET": case "HEAD":
          handleGet(request, response);
          break;
        case "POST":
          handlePost(request, response);
          break;
        default: return false;
        }
      } catch (IOException ex) {
        problem = ex;
      }
    } else {
      return false;
    }

    if (problem != null) {
      callback.failed(problem);
    } else {
      callback.succeeded();
    }
    return true;
  }

  public static void main(String[] argv) throws Exception {
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

  private <T> T or(T a, T b) {
    return a != null ? a : b;
  }
}
