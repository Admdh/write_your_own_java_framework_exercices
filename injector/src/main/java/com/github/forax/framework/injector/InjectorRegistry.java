package com.github.forax.framework.injector;

import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class InjectorRegistry {

  private final HashMap<Class<?>, Supplier<?>> map = new HashMap<>();

  public <T> T lookupInstance(Class<T> typeToken) {
    Objects.requireNonNull(typeToken);
    var val = map.get(typeToken);
    if (val == null) {
      throw new IllegalStateException("no instance of " + typeToken);
    } else {
      return typeToken.cast(val.get());
    }
  }

  public <T> void registerInstance(Class<T> typeToken, T instance) {
    Objects.requireNonNull(typeToken);
    Objects.requireNonNull(instance);
    if (map.putIfAbsent(typeToken, () -> instance) != null) {
      throw new IllegalStateException();
    }
  }

  public <T> void registerProvider(Class<T> typeToken, Supplier<T> provider) {
    Objects.requireNonNull(typeToken);
    Objects.requireNonNull(provider);
    if (map.putIfAbsent(typeToken, provider) != null) {
      throw new IllegalStateException();
    }
  }

  static List<PropertyDescriptor> findInjectableProperties(Class<?> type) {
    var propertyDescriptors = Utils.beanInfo(type).getPropertyDescriptors();
    return Arrays.stream(propertyDescriptors)
      .filter(propertyDescriptor -> {
        var setter = propertyDescriptor.getWriteMethod();
        return setter != null && setter.isAnnotationPresent(Inject.class);
      })
      .toList();
  }

  public <T> void registerProviderClass(Class<T> typeToken, Class<? extends T> beanType) {
    Objects.requireNonNull(typeToken);
    Objects.requireNonNull(beanType);
    var constructionType = beanConstructionType(beanType);
    Supplier<T> supplier = () -> {
      var beanInstance = createBeanInstance(beanType, constructionType);
      findInjectableProperties(beanType)
        .forEach(injectablePropertyDescriptor -> {
          var setter = injectablePropertyDescriptor.getWriteMethod();
          var injectableParameters = setter.getParameterTypes();
          var injectedParameters = Arrays.stream(injectableParameters)
            .map(this::lookupInstance)
            .toArray();
          Utils.invokeMethod(beanInstance, setter, injectedParameters);
        });
      return beanInstance;
    };
    if (map.putIfAbsent(typeToken, supplier) != null) {
      throw new IllegalStateException();
    }
  }

  private enum CreationType {
    INJECTED_CONSTRUCTOR,
    DEFAULT_CONSTRUCTOR
  }

  private <T> T createBeanInstance(Class<T> beanType, CreationType creationType) {
    return switch (creationType) {
      case DEFAULT_CONSTRUCTOR -> Utils.newInstance(Utils.defaultConstructor(beanType));
      case INJECTED_CONSTRUCTOR -> {
        var constructors = Arrays.stream(beanType.getConstructors())
          .filter(constructor -> constructor.isAnnotationPresent(Inject.class))
          .toList();
        var constructor = constructors.getFirst();
        var injectedConstructorParameters = Arrays.stream(constructor.getParameterTypes())
          .map(this::lookupInstance)
          .toArray();
        yield beanType.cast(Utils.newInstance(constructors.getFirst(), injectedConstructorParameters));
      }
    };
  }

  private CreationType beanConstructionType(Class<?> beanType) {
    var constructors = Arrays.stream(beanType.getConstructors())
      .filter(constructor -> constructor.isAnnotationPresent(Inject.class))
      .toList();
    return switch (constructors.size()) {
      case 0 -> {
        Utils.defaultConstructor(beanType); // only to checks if there's a default constructor
        yield CreationType.DEFAULT_CONSTRUCTOR;
      }
      case 1 -> CreationType.INJECTED_CONSTRUCTOR;
      default -> throw new IllegalStateException("Many injected constructors detected for " + beanType);
    };
  }

  public <T> void registerProviderClass(Class<T> typeToken) {
    Objects.requireNonNull(typeToken);
    registerProviderClass(typeToken, typeToken);
  }
}