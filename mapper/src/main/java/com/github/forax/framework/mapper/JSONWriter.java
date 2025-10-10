package com.github.forax.framework.mapper;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class JSONWriter {

  private interface Generator {
    String generate(JSONWriter writer, Object bean);
  }

  private static List<PropertyDescriptor> beanProperties(Class<?> type) {
    var beanInfo = Utils.beanInfo(type);
    return Arrays.stream(beanInfo.getPropertyDescriptors())
      .filter(propertyDescriptor -> !propertyDescriptor.getName().equals("class"))
      .toList();
  }

  private static List<PropertyDescriptor> recordProperties(Class<?> type) {
    if (!type.isRecord()) {
      throw new IllegalArgumentException("type is not record");
    }
    return Arrays.stream(type.getRecordComponents())
      .map(recordComponent -> {
        try {
          return new PropertyDescriptor(recordComponent.getName(), recordComponent.getAccessor(), null);
        } catch (IntrospectionException e) {
          throw new AssertionError(e);
        }
      })
      .toList();
  }

  private static final ClassValue<Generator> BEAN_CACHE = new ClassValue<>() {
    @Override
    protected Generator computeValue(Class<?> type) {
      var propertiesDescriptorList = type.isRecord() ? recordProperties(type) : beanProperties(type);
      return (JSONWriter writer, Object bean) ->
        propertiesDescriptorList.stream()
          .map(propertyDescriptor -> {
            var key = getKeyString(propertyDescriptor);
            var value = writer.toJSON(Utils.invokeMethod(bean, propertyDescriptor.getReadMethod(), null));
            return key + value;
          })
          .collect(Collectors.joining(", ", "{", "}"));
    }
  };

  private static String getKeyString(PropertyDescriptor propertyDescriptor) {
    var annotation = propertyDescriptor.getReadMethod().getAnnotation(JSONProperty.class);
    var key = annotation != null ? annotation.value() : propertyDescriptor.getName();
    return "\"" + key + "\": ";
  }

  private final HashMap<Class<?>, Generator> specificToJsonMap = new HashMap<>();

  public <T> void configure(Class<T> type, Function<? super T, String> function) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(function);
    var shouldBeNull = specificToJsonMap.putIfAbsent(type, (_, object) -> function.apply(type.cast(object)));
    if (shouldBeNull != null) {
      throw new IllegalStateException();
    }
  }

  public String toJSON(Object o) {
    return switch (o) {
      case Integer _, Double _, Float _, Boolean _ -> o.toString();
      case String _ -> "\"" + o + "\"";
      case null -> "null";
      default -> {
        var generator = specificToJsonMap.get(o.getClass());
        if (generator == null) {
          generator = BEAN_CACHE.get(o.getClass());
        }
        yield generator.generate(this, o);
      }
    };
  }
}
