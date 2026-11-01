package com.shutternote;

import android.app.Dialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.WindowManager;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import java.util.ArrayList;

/** Keeps a folder-name draft through screen recreation; only an explicit confirmation writes it. */
public class FilmFolderNameDialog extends DialogFragment {
    static final String RESULT="noteFolderName", TAG="noteFolderNameDialog";
    private EditText name;
    static FilmFolderNameDialog create(String id,String name,boolean creating,ArrayList<String> rolls) {
        FilmFolderNameDialog dialog=new FilmFolderNameDialog(); Bundle args=new Bundle();
        args.putString("id",id); args.putString("name",name); args.putBoolean("create",creating); args.putStringArrayList("rolls",rolls);
        dialog.setArguments(args); return dialog;
    }
    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        name=new EditText(requireContext()); name.setSingleLine(true); name.setHint(R.string.note_folder_name);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        name.setText(state==null?requireArguments().getString("name"):state.getString("name"));
        int padding=Math.round(20*getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box=new android.widget.FrameLayout(requireContext()); box.setPadding(padding,0,padding,0); box.addView(name);
        return new AlertDialog.Builder(requireContext()).setTitle(requireArguments().getBoolean("create")?R.string.note_folder_new:R.string.note_folder_rename)
                .setView(box).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.save,null).create();
    }
    @Override public void onStart() {
        super.onStart(); AlertDialog dialog=(AlertDialog)requireDialog();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (!FilmRollFolders.validName(name.getText().toString())) { name.setError(getString(R.string.note_folder_name_error)); return; }
            Bundle result=new Bundle(requireArguments()); result.putString("name",name.getText().toString().trim());
            getParentFragmentManager().setFragmentResult(RESULT,result); dismiss();
        });
        name.requestFocus(); name.setSelection(name.length());
        if (dialog.getWindow()!=null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        if (name!=null) state.putString("name",name.getText().toString());
        super.onSaveInstanceState(state);
    }
}
