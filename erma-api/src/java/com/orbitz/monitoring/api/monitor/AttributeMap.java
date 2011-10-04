package com.orbitz.monitoring.api.monitor;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.orbitz.monitoring.api.AttributeUndefinedException;
import com.orbitz.monitoring.api.CantCoerceException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;

/**
 * A map-like class that can be used to hold attributes for a Monitor. This class requires that keys
 * are {@link String strings}. Also, it has methods for getting and setting primitives as the values
 * of those attributes. This map does not support null values. Values set to null are removed from
 * the the map.
 * @author Doug Barth
 */

public class AttributeMap implements Serializable {
  private static final Pattern ATTRIBUTE_NAME_PATTERN = Pattern.compile("[a-zA-Z_]+[a-zA-Z_0-9]*");
  private static final long serialVersionUID = 2L;
  
  private final ConcurrentHashMap<String, AttributeHolder> attributes;
  private transient final Logger logger = Logger.getLogger(AttributeMap.class);
  
  /**
   * Creates an empty attribute map
   */
  public AttributeMap() {
    attributes = new ConcurrentHashMap<String, AttributeHolder>();
  }
  
  /**
   * Creates an attribute map with a pre-filled set of attributes. Attributes will be copied from
   * the specified attributeMap.
   * @param attributeMap the initial values. Ignored if null.
   */
  public AttributeMap(final Map<String, Object> attributeMap) {
    this();
    if (attributeMap != null) {
      setAll(attributeMap);
    }
  }
  
  /**
   * Removes all entries from this map
   */
  public void clear() {
    attributes.clear();
  }
  
  /**
   * Creates a view of the list that lazily converts elements of the list that are arrays to
   * {@link List lists}. The conversion is applied recursively.
   * @param items the list to convert
   * @return a view of the list that creates lists for array elements
   */
  private List<?> convertArraysToLists(final List<?> items) {
    return Lists.transform(items, new Function<Object, Object>() {
      public Object apply(final Object in) {
        if (in instanceof Object[]) {
          return convertArraysToLists(Lists.newArrayList((Object[])in));
        }
        return in;
      }
    });
  }
  
  /**
   * Creates a new {@link CompositeAttributeHolder} for a value
   * @param old the existing {@link AttributeHolder}. Note that the value of the holder is ignored
   *        and it does not have to be "{@link CompositeAttributeHolder composite}".
   * @param value the value to be held
   * @return a new {@link CompositeAttributeHolder composite holder} holding the value with its
   *         {@link CompositeAttributeHolder#isSerializable() serializable} attribute set to the
   *         same value as the specified holder.
   */
  protected CompositeAttributeHolder createHolderForValue(final AttributeHolder old,
      final Object value) {
    final CompositeAttributeHolder attributeHolder = new CompositeAttributeHolder(value);
    if (old.isSerializable()) {
      attributeHolder.serializable();
    }
    return attributeHolder;
  }
  
  protected CompositeAttributeHolder createHolderForValue(final Object value) {
    return new CompositeAttributeHolder(value);
  }
  
  /**
   * Finds all {@link CompositeAttributeHolder composite attributes}
   * @return a new map containing the composite attributes
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public Map<String, CompositeAttributeHolder> findCompositeAttributes() {
    return (Map)Maps
        .filterValues(attributes, Predicates.instanceOf(CompositeAttributeHolder.class));
  }
  
  /**
   * Gets the value of a key
   * @param key the key to find
   * @return the value at the specified key
   * @throws AttributeUndefinedException if the key doesn't exist
   */
  public Object get(final String key) {
    if (this.hasAttribute(key)) {
      AttributeHolder attribute = attributes.get(key);
      return (attribute == null) ? attribute : attribute.getValue();
    }
    throw new AttributeUndefinedException(key);
  }
  
  /**
   * Gets all values from this attribute map
   * @return a map of all keys to all values
   */
  @SuppressWarnings("unchecked")
  public <V> Map<String, V> getAll() {
    return Maps.transformValues(attributes, new Function<AttributeHolder, V>() {
      public V apply(final AttributeHolder attribute) {
        return (V)attribute.getValue();
      }
    });
  }
  
  public Map<String, AttributeHolder> getAllAttributeHolders() {
    return new HashMap<String, AttributeHolder>(attributes);
  }
  
  /**
   * Gets the items from this map that have indicated they are {@link Serializable} through
   * {@link AttributeHolder#isSerializable()}.
   * @return a new map of keys to the {@link Serializable} values.
   */
  public <V> Map<String, V> getAllSerializable() {
    final Map<String, V> allSerializable = new HashMap<String, V>();
    for (Entry<String, AttributeHolder> entry : attributes.entrySet()) {
      final AttributeHolder attributeHolder = entry.getValue();
      if (attributeHolder.isSerializable()) {
        @SuppressWarnings("unchecked")
        final V value = (V)attributeHolder.getValue();
        allSerializable.put(entry.getKey(), value);
      }
    }
    return allSerializable;
  }
  
  public boolean getAsBoolean(final String key) {
    final Object value = get(key);
    
    if (value == null) {
      throw new AttributeUndefinedException(key);
    }
    
    if (!(value instanceof Boolean)) {
      if ("true".equalsIgnoreCase(value.toString())) {
        return true;
      }
      else if ("false".equalsIgnoreCase(value.toString())) {
        return false;
      }
      throw new CantCoerceException(key, value, "boolean");
    }
    
    return ((Boolean)value).booleanValue();
  }
  
  public boolean getAsBoolean(final String key, final boolean defaultValue) {
    if (!hasAttribute(key)) {
      return defaultValue;
    }
    else {
      return getAsBoolean(key);
    }
  }
  
  public byte getAsByte(final String key) {
    final Object value = get(key);
    
    if (value == null) {
      throw new AttributeUndefinedException(key);
    }
    
    if (!(value instanceof Number)) {
      try {
        return Byte.parseByte(value.toString());
      }
      catch (final NumberFormatException e) {
        throw new CantCoerceException(key, value, "byte");
      }
    }
    
    return ((Number)value).byteValue();
  }
  
  public byte getAsByte(final String key, final byte defaultValue) {
    if (!(hasAttribute(key))) {
      return defaultValue;
    }
    else {
      return getAsByte(key);
    }
  }
  
  public char getAsChar(final String key) {
    final Object value = get(key);
    
    if (value == null) {
      throw new AttributeUndefinedException(key);
    }
    
    if (!(value instanceof Character)) {
      if (value instanceof String) {
        final String stringValue = (String)value;
        if (stringValue.length() == 1) {
          return stringValue.charAt(0);
        }
      }
      throw new CantCoerceException(key, value, "char");
    }
    
    return ((Character)value).charValue();
  }
  
  public char getAsChar(final String key, final char defaultValue) {
    if (!hasAttribute(key)) {
      return defaultValue;
    }
    else {
      return getAsChar(key);
    }
  }
  
  public double getAsDouble(final String key) {
    final Object value = get(key);
    
    if (value == null) {
      throw new AttributeUndefinedException(key);
    }
    
    if (!(value instanceof Number)) {
      try {
        return Double.parseDouble(value.toString());
      }
      catch (final NumberFormatException e) {
        throw new CantCoerceException(key, value, "double");
      }
    }
    
    return ((Number)value).doubleValue();
  }
  
  public double getAsDouble(final String key, final double defaultValue) {
    if (!(hasAttribute(key))) {
      return defaultValue;
    }
    else {
      return getAsDouble(key);
    }
  }
  
  public float getAsFloat(final String key) {
    final Object value = get(key);
    
    if (value == null) {
      throw new AttributeUndefinedException(key);
    }
    
    if (!(value instanceof Number)) {
      try {
        return Float.parseFloat(value.toString());
      }
      catch (final NumberFormatException e) {
        throw new CantCoerceException(key, value, "float");
      }
    }
    
    return ((Number)value).floatValue();
  }
  
  public float getAsFloat(final String key, final float defaultValue) {
    if (!(hasAttribute(key))) {
      return defaultValue;
    }
    else {
      return getAsFloat(key);
    }
  }
  
  public int getAsInt(final String key) {
    final Object value = get(key);
    
    if (value == null) {
      throw new AttributeUndefinedException(key);
    }
    
    if (!(value instanceof Number)) {
      try {
        return Integer.parseInt(value.toString());
      }
      catch (final NumberFormatException e) {
        throw new CantCoerceException(key, value, "int");
      }
    }
    
    return ((Number)value).intValue();
  }
  
  public int getAsInt(final String key, final int defaultValue) {
    if (!(hasAttribute(key))) {
      return defaultValue;
    }
    else {
      return getAsInt(key);
    }
  }
  
  /**
   * Gets the specified value as a list. If the value is a list or null, it will be returned. If the
   * value is an array, it will be converted to a list.
   * @param key the key of the value to find
   * @return the value
   */
  @SuppressWarnings("unchecked")
  public <T> List<T> getAsList(final String key) {
    Object value = get(key);
    if ((value == null) || (value instanceof List)) {
      return (List<T>)value;
    }
    // TODO: Remove coercion. If the client wants to get a list, they should put a list in.
    if (value instanceof Object[]) {
      final List<?> result = Arrays.asList((Object[])value);
      return Lists.transform(result, new Function<Object, T>() {
        public T apply(final Object in) {
          if (in instanceof Object[]) {
            return (T)convertArraysToLists(Arrays.asList((Object[])in));
          }
          return (T)in;
        }
      });
    }
    throw new CantCoerceException(key, value, "List");
  }
  
  public long getAsLong(final String key) {
    final Object value = get(key);
    
    if (value == null) {
      throw new AttributeUndefinedException(key);
    }
    
    if (!(value instanceof Number)) {
      try {
        return Long.parseLong(value.toString());
      }
      catch (final NumberFormatException e) {
        throw new CantCoerceException(key, value, "long");
      }
    }
    
    return ((Number)value).longValue();
  }
  
  public long getAsLong(final String key, final long defaultValue) {
    if (!(hasAttribute(key))) {
      return defaultValue;
    }
    else {
      return getAsLong(key);
    }
  }
  
  /**
   * Gets a map value from the map
   * @param key the key of the value to find
   * @return the map at the specified key
   */
  @SuppressWarnings("unchecked")
  public <K, V> Map<K, V> getAsMap(final String key) {
    final Object value = get(key);
    if (value instanceof Map) {
      return (Map<K, V>)value;
    }
    else {
      throw new CantCoerceException(key, value, "Map");
    }
  }
  
  /**
   * Gets the value at the specified key as a set
   * @param key the key of the value to retrieve
   * @return the value
   * @throws CantCoerceException if the value is not a set
   */
  @SuppressWarnings("unchecked")
  public <T> Set<T> getAsSet(final String key) {
    Object value = get(key);
    try {
      return (Set<T>)value;
    }
    catch (ClassCastException ex) {
      throw new CantCoerceException(key, value, "Set");
    }
  }
  
  public short getAsShort(final String key) {
    final Object value = get(key);
    
    if (value == null) {
      throw new AttributeUndefinedException(key);
    }
    
    if (!(value instanceof Number)) {
      try {
        return Short.parseShort(value.toString());
      }
      catch (final NumberFormatException e) {
        throw new CantCoerceException(key, value, "short");
      }
    }
    
    return ((Number)value).shortValue();
  }
  
  public short getAsShort(final String key, final short defaultValue) {
    if (!(hasAttribute(key))) {
      return defaultValue;
    }
    else {
      return getAsShort(key);
    }
  }
  
  public String getAsString(final String key) {
    final Object attribute = get(key);
    
    return attribute == null ? null : attribute.toString();
  }
  
  /**
   * Gets the raw map of strings to attribute holders
   * @return the map
   */
  public ConcurrentHashMap<String, AttributeHolder> getAttributes() {
    return attributes;
  }
  
  public boolean hasAttribute(final String key) {
    return attributes.containsKey(key);
  }
  
  protected AttributeHolder internalSetAttribute(final String key, final Object value) {
    final Matcher m = ATTRIBUTE_NAME_PATTERN.matcher(key);
    if (!m.matches()) {
      throw new IllegalArgumentException("Attribute [" + key
          + "] violates attribute name restriction, attribute not added.");
    }
    
    AttributeHolder attributeHolder = attributes.get(key);
    
    if (attributeHolder == null) {
      // create a new holder in the map with the given value
      attributeHolder = createHolderForValue(value);
      attributes.put(key, attributeHolder);
    }
    else {
      // if an existing attribute holder is locked, just ignore the attempt to
      // overwrite its value
      if (attributeHolder.isLocked()) {
        if (logger.isDebugEnabled()) {
          logger.debug("Attempt to overwrite locked attribute with key '" + key + "'");
        }
      }
      else {
        attributeHolder = createHolderForValue(attributeHolder, value);
        attributes.put(key, attributeHolder);
      }
    }
    
    return attributeHolder;
  }
  
  public AttributeHolder set(final String key, final boolean value) {
    return internalSetAttribute(key, Boolean.valueOf(value));
  }
  
  public AttributeHolder set(final String key, final byte value) {
    return internalSetAttribute(key, new Byte(value));
  }
  
  public AttributeHolder set(final String key, final char value) {
    return internalSetAttribute(key, new Character(value));
  }
  
  public AttributeHolder set(final String key, final double value) {
    return internalSetAttribute(key, new Double(value));
  }
  
  public AttributeHolder set(final String key, final float value) {
    return internalSetAttribute(key, new Float(value));
  }
  
  public AttributeHolder set(final String key, final int value) {
    return internalSetAttribute(key, new Integer(value));
  }
  
  public AttributeHolder set(final String key, final long value) {
    return internalSetAttribute(key, new Long(value));
  }
  
  /**
   * Sets a value into the map
   * @param key the key
   * @param value the value, which should not be an {@link AttributeHolder}
   * @return the {@link AttributeHolder} that was created to hold the specified value
   */
  public AttributeHolder set(final String key, final Object value) {
    return internalSetAttribute(key, value);
  }
  
  public AttributeHolder set(final String key, final short value) {
    return internalSetAttribute(key, new Short(value));
  }
  
  public void setAll(final Map attributes) {
    setAllAttributeHolders(attributes);
  }
  
  /**
   * Sets zero or more attribute holders from a collection. If an entry value is an
   * {@link AttributeHolder}, it is cloned and its clone is put in this map. If it is not an
   * {@link AttributeHolder}, it is placed in a new attribute holder and put in this map.
   * @param attributeHolders a map of string keys to their holders or not holders. Really, anything.
   */
  public void setAllAttributeHolders(final Map<String, Object> attributeHolders) {
    if (attributeHolders == null) {
      return;
    }
    for (Entry<String, Object> entry : attributeHolders.entrySet()) {
      final String key = entry.getKey();
      final Object value = entry.getValue();
      if (value != null) {
        if (AttributeHolder.class.isAssignableFrom(value.getClass())) {
          final AttributeHolder original = (AttributeHolder)value;
          final AttributeHolder copy = (AttributeHolder)original.clone();
          getAttributes().put(key, copy);
        }
        else {
          set(key, value);
        }
      }
    }
  }
  
  @Override
  public String toString() {
    return getAll().toString();
  }
  
  public void unset(final String key) {
    attributes.remove(key);
  }
}
