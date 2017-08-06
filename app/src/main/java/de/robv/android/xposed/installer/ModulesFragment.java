package de.robv.android.xposed.installer;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBar;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.installer.repo.RepoDb;
import de.robv.android.xposed.installer.repo.RepoDb.RowNotFoundException;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.ModuleUtil.InstalledModule;
import de.robv.android.xposed.installer.util.ModuleUtil.ModuleListener;
import de.robv.android.xposed.installer.util.NavUtil;
import de.robv.android.xposed.installer.util.ThemeUtil;

import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;

public class ModulesFragment extends ListFragment implements ModuleListener {

    public static final String SETTINGS_CATEGORY = "de.robv.android.xposed.category.MODULE_SETTINGS";

    public static final String PLAY_STORE_PACKAGE = "com.android.vending";
    public static final String PLAY_STORE_LINK = "https://play.google.com/store/apps/details?id=%s";

    private static String PLAY_STORE_LABEL = null;

    private ModuleUtil mModuleUtil;
    private ModuleAdapter mAdapter = null;
    private PackageManager mPm = null;

    private Runnable reloadModules = new Runnable() {
        public void run() {
            mAdapter.setNotifyOnChange(false);
            mAdapter.clear();
            mAdapter.addAll(mModuleUtil.getModules().values());
            final Collator col = Collator.getInstance(Locale.getDefault());
            mAdapter.sort(new Comparator<InstalledModule>() {
                @Override
                public int compare(InstalledModule lhs, InstalledModule rhs) {
                    return col.compare(lhs.getAppName(), rhs.getAppName());
                }
            });
            mAdapter.notifyDataSetChanged();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModuleUtil = ModuleUtil.getInstance();
        mPm = getActivity().getPackageManager();
        if (PLAY_STORE_LABEL == null) {
            try {
                ApplicationInfo ai = mPm.getApplicationInfo(PLAY_STORE_PACKAGE,
                        0);
                PLAY_STORE_LABEL = mPm.getApplicationLabel(ai).toString();
            } catch (NameNotFoundException ignored) {
            }
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mAdapter = new ModuleAdapter(getActivity());
        reloadModules.run();
        setListAdapter(mAdapter);
        setEmptyText(getActivity().getString(R.string.no_xposed_modules_found));
        registerForContextMenu(getListView());
        mModuleUtil.addListener(this);

        ActionBar actionBar = ((WelcomeActivity) getActivity()).getSupportActionBar();

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int sixDp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, metrics);
        int eightDp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, metrics);
        assert actionBar != null;
        int toolBarDp = actionBar.getHeight() == 0 ? 196 : actionBar.getHeight();

        getListView().setDivider(null);
        getListView().setDividerHeight(sixDp);
        getListView().setPadding(eightDp, toolBarDp + eightDp, eightDp, eightDp);
        getListView().setClipToPadding(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mModuleUtil.removeListener(this);
        setListAdapter(null);
        mAdapter = null;
    }

    @Override
    public void onSingleInstalledModuleReloaded(ModuleUtil moduleUtil, String packageName, InstalledModule module) {
        getActivity().runOnUiThread(reloadModules);
    }

    @Override
    public void onInstalledModulesReloaded(ModuleUtil moduleUtil) {
        getActivity().runOnUiThread(reloadModules);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        String packageName = (String) v.getTag();
        if (packageName == null)
            return;

        Intent launchIntent = getSettingsIntent(packageName);
        if (launchIntent != null)
            startActivity(launchIntent);
        else
            Toast.makeText(getActivity(),
                    getActivity().getString(R.string.module_no_ui),
                    Toast.LENGTH_LONG).show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {
        InstalledModule installedModule = getItemFromContextMenuInfo(menuInfo);
        if (installedModule == null)
            return;

        menu.setHeaderTitle(installedModule.getAppName());
        getActivity().getMenuInflater().inflate(R.menu.context_menu_modules, menu);

        if (getSettingsIntent(installedModule.packageName) == null)
            menu.removeItem(R.id.menu_launch);

        try {
            String support = RepoDb
                    .getModuleSupport(installedModule.packageName);
            if (NavUtil.parseURL(support) == null)
                menu.removeItem(R.id.menu_support);
        } catch (RowNotFoundException e) {
            menu.removeItem(R.id.menu_download_updates);
            menu.removeItem(R.id.menu_support);
        }

        String installer = mPm.getInstallerPackageName(installedModule.packageName);
        if (PLAY_STORE_LABEL != null && PLAY_STORE_PACKAGE.equals(installer))
            menu.findItem(R.id.menu_play_store).setTitle(PLAY_STORE_LABEL);
        else
            menu.removeItem(R.id.menu_play_store);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        InstalledModule module = getItemFromContextMenuInfo(item.getMenuInfo());
        if (module == null)
            return false;

        switch (item.getItemId()) {
            case R.id.menu_launch:
                startActivity(getSettingsIntent(module.packageName));
                return true;

            case R.id.menu_download_updates:
                Intent detailsIntent = new Intent(getActivity(), DownloadDetailsActivity.class);
                detailsIntent.setData(Uri.fromParts("package", module.packageName, null));
                startActivity(detailsIntent);
                return true;

            case R.id.menu_support:
                NavUtil.startURL(getActivity(), Uri.parse(RepoDb.getModuleSupport(module.packageName)));
                return true;

            case R.id.menu_play_store:
                Intent i = new Intent(android.content.Intent.ACTION_VIEW);
                i.setData(Uri.parse(String.format(PLAY_STORE_LINK, module.packageName)));
                i.setPackage(PLAY_STORE_PACKAGE);
                try {
                    startActivity(i);
                } catch (ActivityNotFoundException e) {
                    i.setPackage(null);
                    startActivity(i);
                }
                return true;

            case R.id.menu_app_info:
                startActivity(new Intent(ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", module.packageName, null)));
                return true;

            case R.id.menu_uninstall:
                startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.fromParts("package", module.packageName, null)));
                return true;
        }

        return false;
    }

    private InstalledModule getItemFromContextMenuInfo(ContextMenuInfo menuInfo) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        int position = info.position - getListView().getHeaderViewsCount();
        return (position >= 0) ? (InstalledModule) getListAdapter().getItem(position) : null;
    }

    private Intent getSettingsIntent(String packageName) {
        // taken from
        // ApplicationPackageManager.getLaunchIntentForPackage(String)
        // first looks for an Xposed-specific category, falls back to
        // getLaunchIntentForPackage
        PackageManager pm = getActivity().getPackageManager();

        Intent intentToResolve = new Intent(Intent.ACTION_MAIN);
        intentToResolve.addCategory(SETTINGS_CATEGORY);
        intentToResolve.setPackage(packageName);
        List<ResolveInfo> ris = pm.queryIntentActivities(intentToResolve, 0);

        if (ris == null || ris.size() <= 0) {
            return pm.getLaunchIntentForPackage(packageName);
        }

        Intent intent = new Intent(intentToResolve);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(ris.get(0).activityInfo.packageName, ris.get(0).activityInfo.name);
        return intent;
    }

    private class ModuleAdapter extends ArrayAdapter<InstalledModule> {

        ModuleAdapter(Context context) {
            super(context, R.layout.list_item_module, R.id.title);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            if (convertView == null) {
                // The reusable view was created for the first time, set up the
                // listener on the checkbox
                ((Switch) view.findViewById(R.id.checkbox)).setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        String packageName = (String) buttonView.getTag();
                        boolean changed = mModuleUtil.isModuleEnabled(packageName) ^ isChecked;
                        if (changed) {
                            mModuleUtil.setModuleEnabled(packageName, isChecked);
                            mModuleUtil.updateModulesList(true);
                        }
                    }
                });
            }

            InstalledModule item = getItem(position);

            TextView version = (TextView) view.findViewById(R.id.version_name);
            version.setText(item.versionName);

            // Store the package name in some views' tag for later access
            view.findViewById(R.id.checkbox).setTag(item.packageName);
            view.setTag(item.packageName);

            ((ImageView) view.findViewById(R.id.icon)).setImageDrawable(item.getIcon());

            TextView descriptionText = (TextView) view.findViewById(R.id.description);
            if (!item.getDescription().isEmpty()) {
                descriptionText.setText(item.getDescription());
                descriptionText.setTextColor(ThemeUtil.getThemeColor(getContext(), android.R.attr.textColorSecondary));
            } else {
                descriptionText.setText(getString(R.string.module_empty_description));
                descriptionText.setTextColor(getResources().getColor(R.color.warning));
            }

            Switch checkbox = (Switch) view.findViewById(R.id.checkbox);
            checkbox.setChecked(mModuleUtil.isModuleEnabled(item.packageName));
            TextView warningText = (TextView) view.findViewById(R.id.warning);

            if (item.minVersion == 0) {
                checkbox.setEnabled(false);
                warningText.setText(getString(R.string.no_min_version_specified));
                warningText.setVisibility(View.VISIBLE);
            } else if (item.minVersion < ModuleUtil.MIN_MODULE_VERSION) {
                checkbox.setEnabled(false);
                warningText.setText(String.format(getString(R.string.warning_min_version_too_low), item.minVersion, ModuleUtil.MIN_MODULE_VERSION));
                warningText.setVisibility(View.VISIBLE);
            } else if (item.isInstalledOnExternalStorage()) {
                checkbox.setEnabled(false);
                warningText.setText(getString(R.string.warning_installed_on_external_storage));
                warningText.setVisibility(View.VISIBLE);
            } else {
                checkbox.setEnabled(true);
                warningText.setVisibility(View.GONE);
            }
            return view;
        }
    }

}
