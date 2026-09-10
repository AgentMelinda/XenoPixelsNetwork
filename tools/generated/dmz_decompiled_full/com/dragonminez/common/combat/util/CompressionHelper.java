package com.dragonminez.common.combat.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CompressionHelper {
   public static String gzipCompress(String uncompressed) {
      if (uncompressed != null && !uncompressed.isEmpty()) {
         try {
            String var3;
            try (
               ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
               GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream);
            ) {
               gzipStream.write(uncompressed.getBytes());
               gzipStream.close();
               var3 = Base64.getEncoder().encodeToString(byteStream.toByteArray());
            }

            return var3;
         } catch (IOException var9) {
            throw new RuntimeException("Failed to compress string", var9);
         }
      } else {
         return uncompressed;
      }
   }

   public static String gzipDecompress(String compressed) {
      if (compressed != null && !compressed.isEmpty()) {
         try {
            String var6;
            try (
               ByteArrayInputStream byteStream = new ByteArrayInputStream(Base64.getDecoder().decode(compressed));
               GZIPInputStream gzipStream = new GZIPInputStream(byteStream);
               ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
            ) {
               byte[] buffer = new byte[1024];

               int length;
               while ((length = gzipStream.read(buffer)) > 0) {
                  resultStream.write(buffer, 0, length);
               }

               var6 = resultStream.toString();
            }

            return var6;
         } catch (IOException var13) {
            throw new RuntimeException("Failed to decompress string", var13);
         }
      } else {
         return compressed;
      }
   }
}
