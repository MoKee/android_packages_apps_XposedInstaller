package de.robv.android.xposed.installer.installation;

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;
import java.io.IOException;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.util.RootUtil;

public class StatusInstallerFragment extends Fragment {
    public static final File DISABLE_FILE = new File(XposedApp.BASE_DIR + "conf/disabled");

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.status_installer, container, false);

        // Disable switch
        final SwitchCompat disableSwitch = (SwitchCompat) v.findViewById(R.id.disableSwitch);
        disableSwitch.setChecked(!DISABLE_FILE.exists());
        disableSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (DISABLE_FILE.exists()) {
                    DISABLE_FILE.delete();
                    Snackbar.make(disableSwitch, R.string.xposed_on_next_reboot, Snackbar.LENGTH_LONG).show();
                } else {
                    try {
                        DISABLE_FILE.createNewFile();
                        Snackbar.make(disableSwitch, R.string.xposed_off_next_reboot, Snackbar.LENGTH_LONG).show();
                    } catch (IOException e) {
                        Log.e(XposedApp.TAG, "Could not create " + DISABLE_FILE, e);
                    }
                }
            }
        });

        // Display warning dialog to new users
        if (!XposedApp.getPreferences().getBoolean("hide_install_warning", false)) {
            new MaterialDialog.Builder(getActivity())
                    .title(R.string.install_warning_title)
                    .content(R.string.install_warning)
                    .positiveText(android.R.string.ok)
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            if (dialog.isPromptCheckBoxChecked()) {
                                XposedApp.getPreferences().edit().putBoolean("hide_install_warning", true).apply();
                            }
                        }
                    })
                    .checkBoxPromptRes(R.string.dont_show_again, false, null)
                    .cancelable(false)
                    .show();
        }

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshInstallStatus();
    }

    private void refreshInstallStatus() {
        View v = getView();
        TextView txtInstallError = (TextView) v.findViewById(R.id.framework_install_errors);
        View txtInstallContainer = v.findViewById(R.id.status_container);
        ImageView txtInstallIcon = (ImageView) v.findViewById(R.id.status_icon);
        View disableWrapper = v.findViewById(R.id.disableView);

        // TODO This should probably compare the full version string, not just the number part.
        int active = XposedApp.getActiveXposedVersion();
        int installed = XposedApp.getInstalledXposedVersion();
        if (installed < 0) {
            txtInstallError.setText(R.string.framework_not_installed);
            txtInstallError.setTextColor(getResources().getColor(R.color.warning));
            txtInstallContainer.setBackgroundColor(getResources().getColor(R.color.warning));
            txtInstallIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_error));
            disableWrapper.setVisibility(View.GONE);
        } else if (installed != active) {
            txtInstallError.setText(getString(R.string.framework_not_active, XposedApp.getXposedProp().getVersion()));
            txtInstallError.setTextColor(getResources().getColor(R.color.amber_500));
            txtInstallContainer.setBackgroundColor(getResources().getColor(R.color.amber_500));
            txtInstallIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_warning));
        } else {
            txtInstallError.setText(getString(R.string.framework_active, XposedApp.getXposedProp().getVersion()));
            txtInstallError.setTextColor(getResources().getColor(R.color.darker_green));
            txtInstallContainer.setBackgroundColor(getResources().getColor(R.color.darker_green));
            txtInstallIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_check_circle));
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.reboot:
            case R.id.soft_reboot:
            case R.id.reboot_recovery:
                final RootUtil.RebootMode mode = RootUtil.RebootMode.fromId(item.getItemId());
                confirmReboot(mode.titleRes, new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        RootUtil.reboot(mode, getActivity());
                    }
                });
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void confirmReboot(int contentTextId, MaterialDialog.SingleButtonCallback yesHandler) {
        new MaterialDialog.Builder(getActivity())
                .content(R.string.reboot_confirmation)
                .positiveText(contentTextId)
                .negativeText(android.R.string.no)
                .onPositive(yesHandler)
                .show();
    }

}
