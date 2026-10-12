package dev.ahmet;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AddressedChat {
  private static final Pattern ADDRESS = Pattern.compile("(?iu)^\\s*(?:@?ahmet)[,!:;\\s]+(.+)$");

  private AddressedChat() {}

  static Optional<String> parse(String text) {
    if (text == null) return Optional.empty();
    Matcher match = ADDRESS.matcher(text);
    if (!match.matches()) return Optional.empty();
    String message = match.group(1).trim();
    return message.isEmpty() ? Optional.empty() : Optional.of(message);
  }
}
