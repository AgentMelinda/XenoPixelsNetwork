package com.dragonminez.common.compat;

import io.netty.handler.codec.DecoderException;
import java.io.IOException;

public final class SableUdpNoise {
   private SableUdpNoise() {
   }

   public static boolean isInvalidPacketIdNoise(Throwable cause) {
      for (Throwable t = cause; t != null; t = t.getCause()) {
         if (t instanceof DecoderException || t instanceof IOException) {
            String msg = t.getMessage();
            if (msg != null && msg.contains("invalid packet ID")) {
               return true;
            }
         }
      }

      return false;
   }
}
