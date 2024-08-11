package net.irisshaders.iris.compat.voxy;

import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.shader.ShaderCompileException;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.minecraft.client.Minecraft;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;

public class VoxyCompat {
	private static boolean voxyPresent = true;
	private static boolean lastIncompatible;
	private static MethodHandle deletePipeline;
	private static MethodHandle incompatible;
	private static MethodHandle getDepthTex;
	private static MethodHandle checkFrame;
	private static MethodHandle getRenderDistance;

	private Object compatInternalInstance;

	public VoxyCompat(IrisRenderingPipeline pipeline, boolean renderVoxyShadow) {
		try {
			if (voxyPresent) {
				compatInternalInstance = Class.forName("net.irisshaders.iris.compat.voxy.VoxyCompatInternal").getDeclaredConstructor(pipeline.getClass(), boolean.class).newInstance(pipeline, renderVoxyShadow);
				lastIncompatible = (boolean) incompatible.invoke(compatInternalInstance);
			}
		} catch (Throwable e) {
			lastIncompatible = false;
			if (e.getCause() instanceof ShaderCompileException sce) {
				throw sce;
			} else if (e instanceof InvocationTargetException ite) {
				throw new RuntimeException("Unknown error loading Voxy compatibility.", ite.getCause());
			} else {
				throw new RuntimeException("Unknown error loading Voxy compatibility.", e);
			}
		}
	}

	public static void run() {
		try {
			if (FabricLoader.getInstance().isModLoaded("voxy")) {
				deletePipeline = MethodHandles.lookup().findVirtual(Class.forName("net.irisshaders.iris.compat.voxy.VoxyCompatInternal"), "clear", MethodType.methodType(void.class));
				incompatible = MethodHandles.lookup().findVirtual(Class.forName("net.irisshaders.iris.compat.voxy.VoxyCompatInternal"), "incompatiblePack", MethodType.methodType(boolean.class));
				getDepthTex = MethodHandles.lookup().findVirtual(Class.forName("net.irisshaders.iris.compat.voxy.VoxyCompatInternal"), "getStoredDepthTex", MethodType.methodType(int.class));
				checkFrame = MethodHandles.lookup().findStatic(Class.forName("net.irisshaders.iris.compat.voxy.VoxyCompatInternal"), "checkFrame", MethodType.methodType(boolean.class));
				getRenderDistance = MethodHandles.lookup().findStatic(Class.forName("net.irisshaders.iris.compat.voxy.VoxyCompatInternal"), "getRenderDistance", MethodType.methodType(int.class));
			} else {
				voxyPresent = false;
			}
		} catch (Throwable e) {
			voxyPresent = false;

			if (FabricLoader.getInstance().isModLoaded("voxy")) {
				if (e instanceof ExceptionInInitializerError eiie) {
					throw new RuntimeException("Failure loading Voxy compat.", eiie.getCause());
				} else {
					throw new RuntimeException("Voxy found, but one or more API methods are missing. Iris requires Voxy [TODO] or newer. Please make sure you are on the latest version of Voxy and Iris.", e);
				}
			} else {
				Iris.logger.info("Voxy not found, and classes not found.");
			}
		}
	}

	public void clearPipeline() {
		if (compatInternalInstance == null) return;

		try {
			deletePipeline.invoke(compatInternalInstance);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean checkFrame() {
		if (!voxyPresent) {
			return false;
		}

		try {
			return (boolean) checkFrame.invoke();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean hasRenderingEnabled() {
		if (!voxyPresent) {
			return false;
		}

		return checkFrame();
	}

	public static boolean lastPackIncompatible() {
		return voxyPresent && hasRenderingEnabled() && lastIncompatible;
	}

	public static int getRenderDistance() {
		if (!voxyPresent) return Minecraft.getInstance().options.getEffectiveRenderDistance();

		try {
			return (int) getRenderDistance.invoke();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public int getDepthTex() {
		if (compatInternalInstance == null) return -1;

		try {
			return (int) getDepthTex.invoke(compatInternalInstance);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
}
