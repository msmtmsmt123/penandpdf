package com.cgogolin.penandpdf;
import java.util.ArrayList;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap.Config;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.preference.PreferenceManager;
import android.util.Log;

public class MuPDFCore
{
    private static final float INK_THICKNESS=10f;
    
	/* load our native library */
    static {
        System.loadLibrary("mupdf");
    }

	/* Readable members */
    private int numPages = -1;
    private boolean numPagesIsUpToDate = false;
    private float pageWidth;
    private float pageHeight;
    private long globals;
    private byte fileBuffer[];
    private String file_format;
    private String mPath = null;
    private String mFileName = null;
    
	/* The native functions */
    private native long openFile(String filename);
    private native long openBuffer();
    private native String fileFormatInternal();
    private native int countPagesInternal();
    private native void gotoPageInternal(int localActionPageNum);
    private native float getPageWidth();
    private native float getPageHeight();
    private native void drawPage(Bitmap bitmap,
                                 int pageW, int pageH,
                                 int patchX, int patchY,
                                 int patchW, int patchH);
    private native void updatePageInternal(Bitmap bitmap,
                                           int page,
                                           int pageW, int pageH,
                                           int patchX, int patchY,
                                           int patchW, int patchH);
    private native RectF[] searchPage(String text);
    private native TextChar[][][][] text();
    private native byte[] textAsHtml();
    private native void addMarkupAnnotationInternal(PointF[] quadPoints, int type);
    private native void addInkAnnotationInternal(PointF[][] arcs);
    private native void deleteAnnotationInternal(int annot_index);
    private native int passClickEventInternal(int page, float x, float y);
    private native void setFocusedWidgetChoiceSelectedInternal(String [] selected);
    private native String [] getFocusedWidgetChoiceSelected();
    private native String [] getFocusedWidgetChoiceOptions();
    private native int getFocusedWidgetSignatureState();
    private native String checkFocusedSignatureInternal();
    private native boolean signFocusedSignatureInternal(String keyFile, String password);
    private native int setFocusedWidgetTextInternal(String text);
    private native String getFocusedWidgetTextInternal();
    private native int getFocusedWidgetTypeInternal();
    private native LinkInfo [] getPageLinksInternal(int page);
    private native RectF[] getWidgetAreasInternal(int page);
    private native Annotation[] getAnnotationsInternal(int page);
    private native OutlineItem [] getOutlineInternal();
    private native boolean hasOutlineInternal();
    private native boolean needsPasswordInternal();
    private native boolean authenticatePasswordInternal(String password);
    private native MuPDFAlertInternal waitForAlertInternal();
    private native void replyToAlertInternal(MuPDFAlertInternal alert);
    private native void startAlertsInternal();
    private native void stopAlertsInternal();
    private native void destroying();
    private native boolean hasChangesInternal();
//	private native int saveInternal();
    private native int saveAsInternal(String path);
    private native int insertPageBeforeInternal(int position);

    public native void setInkThickness(float inkThickness);
    public native void setInkColor(float r, float g, float b);
    public native void setHighlightColor(float r, float g, float b);
    public native void setUnderlineColor(float r, float g, float b);
    public native void setStrikeoutColor(float r, float g, float b);
    public native int insertBlankPageBeforeInternal(int position);

    
    public static native boolean javascriptSupported();

    public MuPDFCore(Context context, String path) throws Exception
	{
            if(path == null) throw new Exception(String.format(context.getString(R.string.cannot_open_file_Path), path));
                
            mPath = path;
            int lastSlashPos = path.lastIndexOf('/');
            mFileName = new String(lastSlashPos == -1 ? path : path.substring(lastSlashPos+1));
            
            globals = openFile(path);
            if (globals == 0)
            {
                throw new Exception(String.format(context.getString(R.string.cannot_open_file_Path), path));
            }
            file_format = fileFormatInternal();
	}
    
    public MuPDFCore(Context context, byte buffer[], String displayName) throws Exception
	{
            fileBuffer = buffer;
            mFileName = displayName;
                
            globals = openBuffer();
            if (globals == 0)
            {
                throw new Exception(context.getString(R.string.cannot_open_buffer));
            }
            file_format = fileFormatInternal();
	}

    public  int countPages()
	{
            if (numPages < 0 || !numPagesIsUpToDate )
            {
                numPages = countPagesSynchronized();
                numPagesIsUpToDate = true;
            }
            return numPages;
	}

    public String fileFormat()
	{
            return file_format;
	}

    private synchronized int countPagesSynchronized() {
        return countPagesInternal();
    }

	/* Shim function */
    private void gotoPage(int page)
	{
            if (page > countPages()-1)
                page = countPages()-1;
            else if (page < 0)
                page = 0;
            gotoPageInternal(page);
            this.pageWidth = getPageWidth();
            this.pageHeight = getPageHeight();
	}

    public synchronized PointF getPageSize(int page) {
        gotoPage(page);
        return new PointF(pageWidth, pageHeight);
    }

    public MuPDFAlert waitForAlert() {
        MuPDFAlertInternal alert = waitForAlertInternal();
        return alert != null ? alert.toAlert() : null;
    }

    public void replyToAlert(MuPDFAlert alert) {
        replyToAlertInternal(new MuPDFAlertInternal(alert));
    }

    public void stopAlerts() {
        stopAlertsInternal();
    }

    public void startAlerts() {
        startAlertsInternal();
    }

    public synchronized void onDestroy() {
        stopAlerts();
        destroying();
        globals = 0;
    }

    public synchronized void drawPage(Bitmap bm, int page,
                                      int pageW, int pageH,
                                      int patchX, int patchY,
                                      int patchW, int patchH) {
        gotoPage(page);
        drawPage(bm, pageW, pageH, patchX, patchY, patchW, patchH);
    }

    public synchronized void updatePage(Bitmap bm, int page,
                                        int pageW, int pageH,
                                        int patchX, int patchY,
                                        int patchW, int patchH) {
        updatePageInternal(bm, page, pageW, pageH, patchX, patchY, patchW, patchH);
    }

    public synchronized PassClickResult passClickEvent(int page, float x, float y) {
        boolean changed = passClickEventInternal(page, x, y) != 0;

        switch (WidgetType.values()[getFocusedWidgetTypeInternal()])
        {
            case TEXT:
                return new PassClickResultText(changed, getFocusedWidgetTextInternal());
            case LISTBOX:
            case COMBOBOX:
                return new PassClickResultChoice(changed, getFocusedWidgetChoiceOptions(), getFocusedWidgetChoiceSelected());
            case SIGNATURE:
                return new PassClickResultSignature(changed, getFocusedWidgetSignatureState());
            default:
                return new PassClickResult(changed);
        }

    }

    public synchronized boolean setFocusedWidgetText(int page, String text) {
        boolean success;
        gotoPage(page);
        success = setFocusedWidgetTextInternal(text) != 0 ? true : false;

        return success;
    }

    public synchronized void setFocusedWidgetChoiceSelected(String [] selected) {
        setFocusedWidgetChoiceSelectedInternal(selected);
    }

    public synchronized String checkFocusedSignature() {
        return checkFocusedSignatureInternal();
    }

    public synchronized boolean signFocusedSignature(String keyFile, String password) {
        return signFocusedSignatureInternal(keyFile, password);
    }

    public synchronized LinkInfo [] getPageLinks(int page) {
        LinkInfo[] pageLinks = getPageLinksInternal(page);
        if(pageLinks == null) return null;
            // To flip the y cooridnate of all link targets to make coordiante system consistent with the link rect and coordinates of search results
        for (LinkInfo link: pageLinks)
            if(link.type() == LinkInfo.LinkType.Internal)
            {
//                Log.v("Core", "internal link with left="+((LinkInfoInternal)link).target.left+" top="+((LinkInfoInternal)link).target.top+" on page of height="+getPageSize(((LinkInfoInternal)link).pageNumber).y);
                    //The 2s doesn't make any sense...
                ((LinkInfoInternal)link).target.left = 2*((LinkInfoInternal)link).target.left; 
                ((LinkInfoInternal)link).target.top = getPageSize(((LinkInfoInternal)link).pageNumber).y - 2*((LinkInfoInternal)link).target.top ;
                ((LinkInfoInternal)link).target.right = 2*((LinkInfoInternal)link).target.right;
                ((LinkInfoInternal)link).target.bottom = getPageSize(((LinkInfoInternal)link).pageNumber).y - 2*((LinkInfoInternal)link).target.bottom; 
            }
        return pageLinks;
    }

    public synchronized RectF [] getWidgetAreas(int page) {
        return getWidgetAreasInternal(page);
    }

    public synchronized Annotation [] getAnnoations(int page) {
        return getAnnotationsInternal(page);
    }

    public synchronized RectF [] searchPage(int page, String text) {
        gotoPage(page);
        return searchPage(text);
    }

    public synchronized byte[] html(int page) {
        gotoPage(page);
        return textAsHtml();
    }

    public synchronized TextWord [][] textLines(int page) {
        gotoPage(page);
        TextChar[][][][] chars = text();

            // The text of the page held in a hierarchy (blocks, lines, spans).
            // Currently we don't need to distinguish the blocks level or
            // the spans, and we need to collect the text into words.
        ArrayList<TextWord[]> lns = new ArrayList<TextWord[]>();

        for (TextChar[][][] bl: chars) {
            if (bl == null)
                continue;
            for (TextChar[][] ln: bl) {
                ArrayList<TextWord> wds = new ArrayList<TextWord>();
                TextWord wd = new TextWord();

                for (TextChar[] sp: ln) {
                    for (TextChar tc: sp) {
                        final int type = Character.getType(tc.c);
                        final boolean special = Character.isWhitespace(tc.c) || type == Character.END_PUNCTUATION || type == Character.FINAL_QUOTE_PUNCTUATION || type == Character.INITIAL_QUOTE_PUNCTUATION || type == Character.OTHER_PUNCTUATION || type == Character.START_PUNCTUATION;
                        
                        if (special)
                        {
                                //Add what we already have to wds
                            if (wd.w.length() > 0) {
                                wds.add(wd);
//                                Log.v("Core", "'"+wd.w+"' at "+((RectF)wd));
                                wd = new TextWord();
                            }
                        }
//                        if (!Character.isWhitespace(tc.c))
//                        {
                            //Add the character

                       if (Character.isWhitespace(tc.c))
                           Log.v("Core", "tc.c='"+tc.c+"' at "+((RectF)tc));
                        
                        wd.Add(tc);
//                            if (type == Character.END_PUNCTUATION || type == Character.FINAL_QUOTE_PUNCTUATION || type == Character.INITIAL_QUOTE_PUNCTUATION || type == Character.OTHER_PUNCTUATION || type == Character.START_PUNCTUATION)
                        if (special)
                        {
                                //Special chars go into a word on their own
                            wds.add(wd);
                            Log.v("Core", "wd.w='"+wd.w+"' at "+((RectF)wd));
                            wd = new TextWord();
                        }
//                        }
                    }
                }
                
                if (wd.w.length() > 0)
                    wds.add(wd);

                if (wds.size() > 0)
                    lns.add(wds.toArray(new TextWord[wds.size()]));

                for (TextWord word: wds)
                    Log.v("Core", "word='"+word.w+"' at "+word);
            }
        }

        return lns.toArray(new TextWord[lns.size()][]);
    }

    public synchronized void addMarkupAnnotation(int page, PointF[] quadPoints, Annotation.Type type) {
        gotoPage(page);
        addMarkupAnnotationInternal(quadPoints, type.ordinal());
    }

    public synchronized void addInkAnnotation(int page, PointF[][] arcs) {
        gotoPage(page);
        addInkAnnotationInternal(arcs);
    }

    public synchronized void deleteAnnotation(int page, int annot_index) {
        gotoPage(page);
        deleteAnnotationInternal(annot_index);
    }

    public synchronized boolean hasOutline() {
        return hasOutlineInternal();
    }

    public synchronized OutlineItem [] getOutline() {
        return getOutlineInternal();
    }

    public synchronized boolean needsPassword() {
        return needsPasswordInternal();
    }

    public synchronized boolean authenticatePassword(String password) {
        return authenticatePasswordInternal(password);
    }

    public synchronized boolean hasChanges() {
        return hasChangesInternal();
    }

    public synchronized boolean save() {
        return saveAs(null);
    }

    public synchronized boolean saveAs(String path) {
        return saveAsInternal(path)==0 ? true : false;
    }
    
    public String getPath() {
        return mPath;
    }

    public String getFileName() {
        return mFileName;
    }
    
    public void onSharedPreferenceChanged(SharedPreferences sharedPref, String key){
            //Set ink thickness
        float inkThickness = Float.parseFloat(sharedPref.getString(SettingsActivity.PREF_INK_THICKNESS, Float.toString(INK_THICKNESS)));
        setInkThickness(inkThickness*0.5f);
            //Set colors
        int colorNumber;                    
        colorNumber = Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_INK_COLOR, "0" ));
        setInkColor(ColorPalette.getR(colorNumber), ColorPalette.getG(colorNumber), ColorPalette.getB(colorNumber));
        colorNumber = Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_HIGHLIGHT_COLOR, "0" ));
        setHighlightColor(ColorPalette.getR(colorNumber), ColorPalette.getG(colorNumber), ColorPalette.getB(colorNumber));
        colorNumber = Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_UNDERLINE_COLOR, "0" ));
        setUnderlineColor(ColorPalette.getR(colorNumber), ColorPalette.getG(colorNumber), ColorPalette.getB(colorNumber));
        colorNumber = Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_STRIKEOUT_COLOR, "0" ));
        setStrikeoutColor(ColorPalette.getR(colorNumber), ColorPalette.getG(colorNumber), ColorPalette.getB(colorNumber));
    }

    public boolean insertBlankPageAtEnd() {
        return insertBlankPageBefore(countPages());
    }
    
    public boolean insertBlankPageBefore(int position) {
        numPagesIsUpToDate = false;
        return insertBlankPageBeforeInternal(position) == 0 ? true : false;
    }
}
