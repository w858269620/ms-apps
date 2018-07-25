package org.springframework.data.influxdb;

import java.lang.ref.SoftReference;
import java.lang.reflect.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 工具类
 */
public final class InfluxUtils {

    private InfluxUtils() {
    }

//    public static String policy(){
//        // RETENTION POLICY
//        // RetentionPolicy
//        // CREATE DATABASE 'test_database' WITH DURATION 3d REPLICATION 3 SHARDDURATION 30m NAME "liquid"
//    }

    public static final String UTC = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    private static final ThreadLocal<SoftReference<SimpleDateFormat>> SDF_CACHE = new ThreadLocal<>();

    /**
     * 解析UTC格式的时间
     *
     * @param utcTime UTC时间字符串
     * @return 返回解析后的Date
     */
    public static Date parseUTC(String utcTime) {
        SimpleDateFormat utcSdf = null;
        SoftReference<SimpleDateFormat> reference = SDF_CACHE.get();
        if (reference != null) {
            utcSdf = reference.get();
            if (utcSdf == null) {
                utcSdf = new SimpleDateFormat(UTC);
                reference = null;
            }
        }

        if (reference == null) {
            utcSdf = (utcSdf != null ? utcSdf : new SimpleDateFormat(UTC));
            reference = new SoftReference<>(utcSdf);
        }
        SDF_CACHE.set(reference);

        try {
            return utcSdf.parse(utcTime);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 是否为常量(被static和final修饰)
     *
     * @param modifiers 修饰符
     * @return 返回结果
     */
    public static boolean isConst(int modifiers) {
        return Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers);
    }

    /**
     * 是否被static 和 final 修饰
     *
     * @param modifiers 修饰符
     * @return 返回结果
     */
    public static boolean isStaticOrFinal(int modifiers) {
        return Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers);
    }

    /**
     * 是否为静态或者final字段
     *
     * @param field 字段
     * @return 返回判断的值
     */
    public static boolean isStaticOrFinal(Field field) {
        return field == null || isStaticOrFinal(field.getModifiers());
    }

    /**
     * 获取某个字段
     *
     * @param type  类型
     * @param field 字段
     * @return 返回获取的字段对象
     */
    public static Field getField(Class<?> type, String field) {
        if (isNonNull(type, field)
                && !field.isEmpty()
                && type != Object.class) {
            try {
                return type.getDeclaredField(field);
            } catch (NoSuchFieldException e) {/* ignore */}
            return getField(type.getSuperclass(), field);
        }
        return null;
    }

    /**
     * 获取存储类字段的Map
     *
     * @param type      类
     * @param predicate 过滤
     * @param handler   回调
     * @return 返回存储字段的Map
     */
    public static void foreachField(final Class<?> type,
                                    final Predicate<Field> predicate,
                                    final Handler<Field> handler) {
        if (type == null || type == Object.class) {
            return;
        }
        Field[] fields = type.getDeclaredFields();
        for (Field field : fields) {
            if (predicate != null) {
                if (predicate.test(field)) {
                    handler.onHandle(field);
                }
            } else {
                handler.onHandle(field);
            }
        }
        foreachField(type.getSuperclass(), predicate, handler);
    }

    /**
     * 获取存储类字段的Map
     *
     * @param type 类
     * @return 返回存储字段的Map
     */
    public static Map<String, Field> getFieldMap(
            Class<?> type, Predicate<Field> predicate) {
        Map<String, Field> fieldMap = new ConcurrentHashMap<>();
        return getFieldMap(type, fieldMap, predicate);
    }

    /**
     * 获取存储类字段的Map
     *
     * @param type      类
     * @param fieldMap  存储字段的Map
     * @param predicate 过滤
     * @return 返回存储字段的Map
     */
    public static Map<String, Field> getFieldMap(final Class<?> type,
                                                 final Map<String, Field> fieldMap,
                                                 final Predicate<Field> predicate) {
        foreachField(type, predicate, field -> fieldMap.putIfAbsent(field.getName(), field));
        return fieldMap;
    }

    /**
     * 给对象中某个字段设置值
     *
     * @param o     对象
     * @param field 字段
     * @param value 值
     * @param <T>   类型
     */
    public static <T> void setFieldValue(T o, Field field, Object value) {
        if (field != null && o != null) {
            setAccessible(field, true);
            try {
                field.set(o, value);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取对象某字段的值
     *
     * @param field 字段对象
     * @param o     对象
     * @return 返回获取的值
     */
    public static <T> Object getFieldValue(Field field, T o) {
        return getFieldValue(field, o, null);
    }

    /**
     * 获取对象某字段的值
     *
     * @param field 字段对象
     * @param o     对象
     * @return 返回获取的值
     */
    public static Object getFieldValue(Field field, Object o, Object defaultValue) {
        if (isNonNull(o, field)) {
            setAccessible(field, true);
            try {
                return field.get(o);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return defaultValue;
    }

    /**
     * 获取基本的字段和值对象
     *
     * @param o 对象
     * @return 返回字段集合对应的值
     */
    public static Map<String, Object> getFieldValues(Object o) {
        if (o == null || o.getClass() == Object.class) {
            return new LinkedHashMap<>(0);
        }
        Class<?> type = o.getClass();
        Map<String, Field> fieldMap = getFieldMap(type, field -> !isStaticOrFinal(field));
        return getFieldValues(fieldMap, o);
    }

    /**
     * 获取基本的字段和值对象
     *
     * @param fieldMap 字段集合
     * @param o        对象
     * @return 返回字段集合对应的值
     */
    public static Map<String, Object> getFieldValues(Map<String, Field> fieldMap, Object o) {
        if (fieldMap == null || fieldMap.isEmpty()) {
            return new LinkedHashMap<>(0);
        }
        Map<String, Object> values = new LinkedHashMap<>(fieldMap.size());
        fieldMap.forEach((name, field) -> {
            Object value = getFieldValue(field, o);
            if (value != null
                    && value.getClass() != Object.class
                    && !(value instanceof java.util.Date)) {
                value = getFieldValues(value);
            }
            values.put(name, value);
        });
        return values;
    }

    /**
     * 根据构造函数实例化某个类的对象
     *
     * @param constructor 构造函数
     * @param <T>         类型
     * @return 返回实例的对象
     */
    public static <T> T newInstance(Constructor<T> constructor) {
        if (constructor != null) {
            setAccessible(constructor, true);
            try {
                return constructor.newInstance();
            } catch (InstantiationException
                    | IllegalAccessException
                    | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 获取某个类的构造函数
     *
     * @param type           类
     * @param parameterTypes 参数列表
     * @param <T>            类型
     * @return 返回获取的构造函数，如果没有对应参数的构造函数，返回null
     */
    public static <T> Constructor<T> getConstructorOne(
            Class<T> type, Class<?>... parameterTypes) {
        return getConstructorAny(type, true, false, parameterTypes);
    }

    /**
     * 获取声明的构造函数
     *
     * @param type           类
     * @param parameterTypes 参数列表
     * @param <T>            类型
     * @return 返回对应的构造函数
     */
    public static <T> Constructor<T> getDeclaredConstructor(
            Class<T> type, Class<?>... parameterTypes) {
        return getConstructorAny(type, false, true);
    }

    /**
     * 获取某个类的构造函数
     *
     * @param type           类
     * @param force          是否强制获取
     * @param declared       是否声明
     * @param parameterTypes 参数列表
     * @param <T>            类型
     * @return 返回对应的构造函数
     */
    private static <T> Constructor<T> getConstructorAny(
            Class<T> type, boolean force, boolean declared, Class<?>... parameterTypes) {
        if (force || declared) {
            try {
                return type.getDeclaredConstructor(parameterTypes);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }

        if (force || !declared) {
            try {
                return type.getConstructor(parameterTypes);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 设置是否可以访问
     *
     * @param ao   可访问对象
     * @param flag 是否可以访问
     */
    public static void setAccessible(AccessibleObject ao, boolean flag) {
        if (ao == null) {
            return;
        }
        if (!(flag && ao.isAccessible())) {
            ao.setAccessible(flag);
        }
    }

    /**
     * 回调
     *
     * @param <T>
     */
    public interface Handler<T> {
        void onHandle(T t);
    }


    public interface Apply<T, V> {
        /**
         * @param t
         * @return
         */
        V apply(T t);
    }

    public static boolean isNonNull(Object... os) {
        for (Object o : os) {
            if (o == null) {
                return false;
            }
        }
        return true;
    }
}
