package com.dragonminez.common.network;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CompressionUtil {
   public static byte[] compress(String str) {
      if (str != null && !str.isEmpty()) {
         try {
            byte[] var3;
            try (
               ByteArrayOutputStream bos = new ByteArrayOutputStream(str.length());
               GZIPOutputStream gzip = new GZIPOutputStream(bos);
            ) {
               gzip.write(str.getBytes(StandardCharsets.UTF_8));
               gzip.close();
               var3 = bos.toByteArray();
            }

            return var3;
         } catch (IOException var9) {
            throw new RuntimeException("Error compressing packet data", var9);
         }
      } else {
         return new byte[0];
      }
   }

   public static String decompress(byte[] bytes) {
      if (bytes != null && bytes.length != 0) {
         try {
            String var5;
            try (
               GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(bytes));
               BufferedReader br = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8));
            ) {
               StringBuilder sb = new StringBuilder();

               String line;
               while ((line = br.readLine()) != null) {
                  sb.append(line);
               }

               var5 = sb.toString();
            }

            return var5;
         } catch (IOException var10) {
            throw new RuntimeException("Error decompressing packet data", var10);
         }
      } else {
         return "";
      }
   }
}
