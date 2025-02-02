package org.embeddedt.modernfix.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class LambdaMap<K, V> implements Map<K, V> {
    private final Function<K, V> mapSupplier;

    public LambdaMap(Function<K, V> supplier) {
        this.mapSupplier = supplier;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean containsKey(Object o) {
        return true;
    }

    @Override
    public boolean containsValue(Object o) {
        return true;
    }

    @Override
    public V get(Object o) {
        return this.mapSupplier.apply((K)o);
    }

    @Nullable
    @Override
    public V put(K k, V v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> map) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {

    }

    @NotNull
    @Override
    public Set<K> keySet() {
        return Collections.emptySet();
    }

    @NotNull
    @Override
    public Collection<V> values() {
        return Collections.emptyList();
    }

    @NotNull
    @Override
    public Set<Entry<K, V>> entrySet() {
        return Collections.emptySet();
    }
}
