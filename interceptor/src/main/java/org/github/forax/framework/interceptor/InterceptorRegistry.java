package org.github.forax.framework.interceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class InterceptorRegistry {

  private final HashMap<Class<? extends Annotation>, List<Interceptor>> interceptorMap = new HashMap<>();

  private final HashMap<Method, Invocation> invocationCache = new HashMap<>();

  public void addAroundAdvice(Class<? extends Annotation> annotationType, AroundAdvice aroundAdvice) {
    Objects.requireNonNull(aroundAdvice);
    Objects.requireNonNull(annotationType);
    invocationCache.clear();
    Interceptor aroundAdviceWrapper = (instance, method, args, invocation) -> {
      aroundAdvice.before(instance, method, args);
      var returned = invocation.proceed(instance, method, args);
      aroundAdvice.after(instance, method, args, returned);
      return returned;
    };
    interceptorMap.computeIfAbsent(annotationType, _ -> new ArrayList<>()).add(aroundAdviceWrapper);
  }

  public <T> T createProxy(Class<T> proxyType, T instance) {
    Objects.requireNonNull(proxyType);
    Objects.requireNonNull(instance);
    if (!proxyType.isInterface() && !proxyType.isInstance(instance)) {
      throw new IllegalArgumentException(proxyType.getName() + " is not an interface");
    }
    return proxyType.cast(Proxy.newProxyInstance(
      proxyType.getClassLoader(),
      new Class<?>[]{proxyType},
      (_, method, args) ->
        invocationCache.computeIfAbsent(method, met -> getInvocation(findInterceptors(met))).proceed(instance, method, args)));
  }

/*  List<AroundAdvice> findAdvices(Method method) {
    return Arrays.stream(method.getDeclaredAnnotations())
      .filter(annotation -> advicesMap.containsKey(annotation.annotationType()))
      .flatMap(advicedAnnotation -> advicesMap.get(advicedAnnotation.annotationType()).stream())
      .toList();
  }*/

  public void addInterceptor(Class<? extends Annotation> annotation, Interceptor interceptor) {
    Objects.requireNonNull(interceptor);
    Objects.requireNonNull(annotation);
    invocationCache.clear();
    interceptorMap.computeIfAbsent(annotation, _ -> new ArrayList<>()).add(interceptor);
  }

  public List<Interceptor> findInterceptors(Method method) {
    Objects.requireNonNull(method);
    return Stream.of(
        Arrays.stream(method.getDeclaringClass().getAnnotations()),
        Arrays.stream(method.getAnnotations()),
        Arrays.stream(method.getParameterAnnotations()).flatMap(Arrays::stream))
      .flatMap(annotationStream -> annotationStream)
      .distinct()
      .filter(annotation -> interceptorMap.containsKey(annotation.annotationType()))
      .flatMap(advicedAnnotation -> interceptorMap.get(advicedAnnotation.annotationType()).stream())
      .toList();
  }

  public static Invocation getInvocation(List<Interceptor> interceptorList) {
    Objects.requireNonNull(interceptorList);
    Invocation invocation = Utils::invokeMethod;
    for (var interceptor : interceptorList.reversed()) {
      var invocationFinalCopy = invocation;
      Invocation localInv = (instance, method, args) -> interceptor.intercept(instance, method, args, invocationFinalCopy);
      invocation = localInv;
    }
    return invocation;
  }

}
