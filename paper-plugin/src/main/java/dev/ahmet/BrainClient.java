package dev.ahmet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

final class BrainClient {
  private final JavaPlugin plugin;
  private final HttpClient client=HttpClient.newHttpClient();
  BrainClient(JavaPlugin plugin) {this.plugin=plugin;}
  CompletableFuture<JsonObject> post(String path,JsonObject payload) {
    final String base=plugin.getConfig().getString("brain.url", "http://127.0.0.1:3197");
    final String token=plugin.getConfig().getString("brain.token", "");
    final URI uri;
    try {
      uri=URI.create(base+path);
      if(!"http".equals(uri.getScheme()) || uri.getHost()==null || !(uri.getHost().equals("127.0.0.1") || uri.getHost().equals("localhost")))
        return CompletableFuture.failedFuture(new IllegalArgumentException("Brain connection must be local HTTP only"));
    }catch(Exception e){return CompletableFuture.failedFuture(e);}
    final int timeout=Math.max(1,Math.min(30,plugin.getConfig().getInt("brain.timeout-seconds",14)));
    HttpRequest req=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(timeout))
      .header("Authorization","Bearer "+token).header("Content-Type","application/json")
      .POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
    return client.sendAsync(req,HttpResponse.BodyHandlers.ofString()).thenApply(response->{
      if(response.statusCode()!=200)throw new IllegalStateException("Brain HTTP "+response.statusCode());
      return JsonParser.parseString(response.body()).getAsJsonObject();
    });
  }
}
