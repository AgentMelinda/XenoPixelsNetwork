package com.dragonminez.compat.capabilities;

public final class Capability<T> {
   final Class<T> type;
   final String name;

   Capability(Class<T> type, String name) {
      this.type = type;
      this.name = name;
   }

   public Class<T> getType() {
      return this.type;
   }

   public String getName() {
      return this.name;
   }
}
