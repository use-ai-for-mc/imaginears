package com.chenweikeng.imears.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpenAudioMcServiceTest {
  @Test
  void acceptsSupportedOpenAudioMcSessionUrlForms() {
    assertTrue(OpenAudioMcService.isOpenAudioMcUrl("https://session.openaudiomc.net"));
    assertTrue(OpenAudioMcService.isOpenAudioMcUrl("https://session.openaudiomc.net/session/abc"));
    assertTrue(OpenAudioMcService.isOpenAudioMcUrl("https://session.openaudiomc.net#token"));
    assertTrue(OpenAudioMcService.isOpenAudioMcUrl("https://audio.imaginears.club/#token"));
  }

  @Test
  void rejectsLookalikeHostsAndUnsupportedSchemes() {
    assertFalse(OpenAudioMcService.isOpenAudioMcUrl("http://session.openaudiomc.net#token"));
    assertFalse(OpenAudioMcService.isOpenAudioMcUrl("https://session.openaudiomc.net.example.com#token"));
    assertFalse(OpenAudioMcService.isOpenAudioMcUrl("https://example.com/session.openaudiomc.net#token"));
    assertFalse(OpenAudioMcService.isOpenAudioMcUrl("https://audio.imaginears.club.example.com/#token"));
  }

  @Test
  void stripsLiteralLegacyFormattingCodes() {
    assertEquals(
        "You are now connected with the audio client!",
        OpenAudioMcService.stripLegacyFormatting(
            "\u00A72\u00A7oYou are now connected with the audio client!"));
  }
}
