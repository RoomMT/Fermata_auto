package me.aap.fermata.vfs;

import static me.aap.fermata.BuildConfig.ENABLE_GS;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.failed;

import android.content.Context;

import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.vfs.m3u.M3uFileSystem;
import me.aap.fermata.vfs.m3u.M3uFileSystemProvider;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.module.DynamicModuleInstaller;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.activity.ActivityBase;
import me.aap.utils.vfs.VfsException;
import me.aap.utils.vfs.VfsManager;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.content.ContentFileSystem;
import me.aap.utils.vfs.generic.GenericFileSystem;
import me.aap.utils.vfs.local.LocalFileSystem;

/**
 * @author Andrey Pavlenko
 */
public class FermataVfsManager extends VfsManager {
	public static final String M3U_ID = "m3u";

	public FermataVfsManager() {
		super(filesystems());
	}

	public FutureSupplier<VfsProvider> getProvider(String scheme) {
		switch (scheme) {
			case M3U_ID:
				return completed(new M3uFileSystemProvider());
			default:
				return completedNull();
		}
	}

	private static List<VirtualFileSystem> filesystems() {
		FermataApplication app = FermataApplication.get();
		PreferenceStore ps = app.getPreferenceStore();
		List<VirtualFileSystem> p = new ArrayList<>(4);
		try {
			p.add(LocalFileSystem.Provider.getInstance().createFileSystem(ps).getOrThrow());
		} catch (Throwable t) {
			Log.e(t);
		}
		try {
			p.add(GenericFileSystem.Provider.getInstance().createFileSystem(ps).getOrThrow());
		} catch (Throwable t) {
			Log.e(t);
		}
		try {
			p.add(ContentFileSystem.Provider.getInstance().createFileSystem(ps).getOrThrow());
		} catch (Throwable t) {
			Log.e(t);
		}
		try {
			p.add(M3uFileSystem.Provider.getInstance().createFileSystem(ps).getOrThrow());
		} catch (Throwable t) {
			Log.e(t);
		}
		return p;
	}
}
