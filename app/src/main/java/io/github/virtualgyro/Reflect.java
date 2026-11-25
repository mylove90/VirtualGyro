package io.github.virtualgyro;

import java.lang.reflect.Field;

/** Tiny reflection helper; every call is best effort and never throws. */
final class Reflect {
    private Reflect() {
    }

    static boolean setField(Object target, String name, Object value) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return true;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }
}
