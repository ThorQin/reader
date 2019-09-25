package com.github.thorqin.reader.utils

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.net.NetworkInterface.getNetworkInterfaces
import java.net.SocketException


@Target(AnnotationTarget.FIELD)
annotation class Skip

class Exclusion: ExclusionStrategy {
	override fun shouldSkipClass(clazz: Class<*>?): Boolean {
		return false
	}
	override fun shouldSkipField(f: FieldAttributes?): Boolean {
		if (f == null) {
			return true
		}
		return f.getAnnotation(Skip::class.java) != null
	}
}

fun json(): Gson {
	return GsonBuilder()
		.addSerializationExclusionStrategy(Exclusion())
		.addDeserializationExclusionStrategy(Exclusion())
		.create()
}

private class ArrayTypeImpl(internal var clazz: Class<*>) : ParameterizedType {
	override fun getActualTypeArguments(): Array<Type> {
		return arrayOf(clazz)
	}
	override fun getRawType(): Type {
		return List::class.java
	}
	override fun getOwnerType(): Type? {
		return null
	}
}

fun <T> makeListType(type: Class<T>): Type {
	return ArrayTypeImpl(type)
}

/*
private class MapTypeImpl(internal var keyClazz: Class<*>, internal var valueClazz: Class<*>) :
	ParameterizedType {
	override fun getActualTypeArguments(): Array<Type> {
		return arrayOf(keyClazz, valueClazz)
	}
	override fun getRawType(): Type {
		return Map::class.java
	}
	override fun getOwnerType(): Type? {
		return null
	}
}

fun <K, V> makeMapType(keyType: Class<K>, valueType: Class<V>): Type {
	return MapTypeImpl(keyType, valueType)
}
*/

fun getLocalIpAddress(): String? {
	try {
		val infos = getNetworkInterfaces()
		while (infos.hasMoreElements()) {
			val niFace = infos.nextElement()
			val enumIpAddr = niFace.inetAddresses
			while (enumIpAddr.hasMoreElements()) {
				val mInetAddress = enumIpAddr.nextElement()
				if (!mInetAddress.isLoopbackAddress) {
					return mInetAddress.hostAddress.toString()
				}
			}
		}
	} catch (e: SocketException) {

	}
	return null
}
