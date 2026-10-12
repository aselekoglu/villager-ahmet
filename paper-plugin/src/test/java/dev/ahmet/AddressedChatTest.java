package dev.ahmet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

final class AddressedChatTest {
  @Test
  void extractsTurkishCommandAfterAhmetAddress() {
    assertEquals(Optional.of("20 tane meşe odunu topla"), AddressedChat.parse("Ahmet, 20 tane meşe odunu topla"));
  }

  @Test
  void acceptsAddressWithoutPunctuationAndAtMention() {
    assertEquals(Optional.of("selam"), AddressedChat.parse("Ahmet selam"));
    assertEquals(Optional.of("selam"), AddressedChat.parse("@Ahmet: selam"));
  }

  @Test
  void ignoresUnaddressedAndEmptyMessages() {
    assertTrue(AddressedChat.parse("selam Ahmet").isEmpty());
    assertTrue(AddressedChat.parse("Ahmet,").isEmpty());
  }
}
