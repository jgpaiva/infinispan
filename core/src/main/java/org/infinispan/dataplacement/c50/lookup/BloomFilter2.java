package org.infinispan.dataplacement.c50.lookup;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.BitSet;
import java.util.Collection;
import java.util.Random;

public class BloomFilter2 implements Serializable {

   private int numHash, filterSize;
   private double bitsPerElement;
   private BitSet filter;
   private double falsePositiveRate;

   //num queries = 1
   public BloomFilter2(Collection<Object> objects, double falsePositiveRate){
      this.falsePositiveRate = falsePositiveRate;
      bitsPerElement = Math.log(falsePositiveRate) / Math.log(0.6185);
      numHash = (int) Math.ceil(Math.log(2) * bitsPerElement);

      filter = new BitSet((int) Math.ceil(bitsPerElement * objects.size()));
      filterSize = filter.size();

      for (Object obj : objects) {
         accessFilterPositions(convertObjectToByteArray(obj), true);
      }
   }

   public BitSet getFilter(){
      return filter;
   }

   public int getnumHash(){
      return numHash;
   }

   public double getFalsePositiveRate(){
      return falsePositiveRate;
   }

   public boolean contains(Object object) {
      return accessFilterPositions(convertObjectToByteArray(object), false);
   }

   private byte[] convertObjectToByteArray(Object object) {
      byte[] array = new byte[0];
      try {
         ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
         ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
         objectOutputStream.writeObject(object);
         objectOutputStream.flush();

         array = byteArrayOutputStream.toByteArray();

         byteArrayOutputStream.close();
         objectOutputStream.close();
      } catch (Exception e) {
         //no-op
      }
      return array;
   }

   private byte[] convertObjectToByteArray3(Object object) {
      int value = object.hashCode();
      return new byte[] {
            (byte)(value >>> 24),
            (byte)(value >>> 16),
            (byte)(value >>> 8),
            (byte)value};
   }

   private byte[] convertObjectToByteArray2(Object object) {
      Random random = new Random(object.hashCode());
      byte[] array = new byte[32];
      random.nextBytes(array);
      return array;
   }

   private boolean accessFilterPositions(byte[] elementId, boolean set) {
      int hash1 = murmurHash(elementId, elementId.length, 0);
      int hash2 = murmurHash(elementId, elementId.length, hash1);
      for (int i = 0; i < numHash; i++){
         if(set)
            filter.set(Math.abs((hash1 + i * hash2) % filterSize), true);
         else
         if(!filter.get(Math.abs((hash1 + i * hash2) % filterSize)))
            return false;
      }
      return true;
   }

   private int murmurHash(byte[] data, int length, int seed) {
      int m = 0x5bd1e995;
      int r = 24;

      int h = seed ^ length;

      int len_4 = length >> 2;

      for (int i = 0; i < len_4; i++) {
         int i_4 = i << 2;
         int k = data[i_4 + 3];
         k = k << 8;
         k = k | (data[i_4 + 2] & 0xff);
         k = k << 8;
         k = k | (data[i_4 + 1] & 0xff);
         k = k << 8;
         k = k | (data[i_4 + 0] & 0xff);
         k *= m;
         k ^= k >>> r;
         k *= m;
         h *= m;
         h ^= k;
      }

      // avoid calculating modulo
      int len_m = len_4 << 2;
      int left = length - len_m;

      if (left != 0) {
         if (left >= 3) {
            h ^= (int) data[length - 3] << 16;
         }
         if (left >= 2) {
            h ^= (int) data[length - 2] << 8;
         }
         if (left >= 1) {
            h ^= (int) data[length - 1];
         }

         h *= m;
      }

      h ^= h >>> 13;
      h *= m;
      h ^= h >>> 15;

      return h;
   }
}