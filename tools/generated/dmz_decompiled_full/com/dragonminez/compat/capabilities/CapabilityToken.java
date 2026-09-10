package com.dragonminez.compat.capabilities;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class CapabilityToken<T> {
   Class<T> capture() {
      if (this.getClass().getGenericSuperclass() instanceof ParameterizedType parameterized) {
         Type arg = parameterized.getActualTypeArguments()[0];
         if (arg instanceof Class) {
            return (Class<T>)arg;
         }
      }

      throw new IllegalStateException("CapabilityToken must be used as an anonymous class with a concrete type argument");
   }
}
