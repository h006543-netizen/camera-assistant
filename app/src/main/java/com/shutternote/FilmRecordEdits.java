package com.shutternote;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.TimeZone;

/** Applies a single field to the latest record, without replacing unrelated fields. */
final class FilmRecordEdits {
    enum Field { CUT_MEMO, EXPOSURE, SHOT_FOCAL, NAME, FILM, CAMERA, FOCAL, COUNT, PERIOD, MEMO, STATUS, LAB, RECEIVED, COMPLETED, LAB_MEMO }

    static String[] values(FilmRollRecord r, FilmFrameRecord f, Field field) {
        switch (field) {
            case CUT_MEMO: return new String[]{f.memo};
            case EXPOSURE: return new String[]{f.iso, f.aperture, f.shutter, f.fieldOfView};
            case SHOT_FOCAL: return new String[]{f.fieldOfView};
            case NAME: return new String[]{r.name};
            case FILM: return new String[]{r.filmId, r.filmDisplayName()};
            case CAMERA: return new String[]{r.cameraId, r.cameraName};
            case FOCAL: return new String[]{r.focalLength};
            case COUNT: return new String[]{String.valueOf(r.plannedFrames)};
            case PERIOD: return new String[]{FilmRollRules.date(r.start), FilmRollRules.date(r.end)};
            case MEMO: return new String[]{r.memo};
            case STATUS: return new String[]{String.valueOf(r.labStatus)};
            case LAB: return new String[]{r.labName};
            case RECEIVED: return new String[]{FilmRollRules.date(r.received)};
            case COMPLETED: return new String[]{FilmRollRules.date(r.completed)};
            case LAB_MEMO: return new String[]{r.labMemo};
            default: throw new IllegalArgumentException();
        }
    }

    static long parseDate(String value) {
        if (value.isEmpty()) return FilmRollRules.NO_DATE;
        if (!value.matches("[0-9]{4}\\.[0-9]{2}\\.[0-9]{2}")) throw new IllegalArgumentException();
        SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC")); format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        java.util.Date date = format.parse(value, position);
        if (date == null || position.getIndex() != value.length()) throw new IllegalArgumentException();
        return date.getTime();
    }

    /** Zero means valid. Validation is completed before any field is changed. */
    static int apply(FilmRollRecord r, int cut, String capture, Field field, String[] v) {
        FilmFrameRecord f = r.frame(cut);
        if (f == null || !f.captureId.equals(capture)) return R.string.note_frame_missing;
        if (v == null || v.length != values(r, f, field).length) return R.string.note_edit_invalid;
        for (String s : v) if (s == null || s.length() > 1000) return R.string.note_edit_invalid;
        try {
            switch (field) {
                case CUT_MEMO: f.memo = v[0]; break;
                case SHOT_FOCAL:
                    if (!ExposureSettingsStore.isValidFieldOfView(v[0])) return R.string.note_edit_invalid;
                    f.fieldOfView = v[0]; break;
                case EXPOSURE:
                    if (!ExposureSettingsStore.isValidIso(v[0]) || !ExposureSettingsStore.isValidAperture(v[1])
                            || !ExposureSettingsStore.isValidShutter(v[2]) || !ExposureSettingsStore.isValidFieldOfView(v[3])) return R.string.note_edit_invalid;
                    f.iso = v[0]; f.aperture = v[1]; f.shutter = v[2]; f.fieldOfView = v[3]; break;
                case NAME: r.name = v[0].trim(); break;
                case FILM:
                    if (v[1].trim().isEmpty()) return R.string.roll_error_film;
                    r.filmId = v[0]; r.filmName = v[1].trim(); break;
                case CAMERA: r.cameraId = v[1].trim().isEmpty() ? "" : v[0]; r.cameraName = v[1].trim(); break;
                case FOCAL:
                    if (!FilmRollRules.validFocalLength(v[0])) return R.string.roll_error_focal;
                    r.focalLength = v[0]; break;
                case COUNT:
                    int count = FilmRollRules.parseFrameCount(v[0]);
                    if (count < 1) return R.string.roll_error_count;
                    for (FilmFrameRecord frame : r.frames) if (frame.number > count) return R.string.note_edit_count_recorded;
                    r.plannedFrames = count; r.countMode = count == 24 || count == 36 ? count : 0; break;
                case PERIOD:
                    long start = parseDate(v[0]), end = parseDate(v[1]);
                    if (!FilmRollRules.validPeriod(start, end)) return R.string.roll_error_period;
                    r.start = start; r.end = end; break;
                case MEMO: r.memo = v[0]; break;
                case STATUS:
                    if (!Arrays.asList("0", "1", "2").contains(v[0])) return R.string.note_edit_invalid;
                    r.labStatus = Integer.parseInt(v[0]); break;
                case LAB: r.labName = v[0].trim(); break;
                case RECEIVED:
                    long received = parseDate(v[0]);
                    if (!FilmRollRules.validLabDates(received, r.completed)) return R.string.roll_error_lab_dates;
                    r.received = received; break;
                case COMPLETED:
                    long completed = parseDate(v[0]);
                    if (!FilmRollRules.validLabDates(r.received, completed)) return R.string.roll_error_lab_dates;
                    r.completed = completed; break;
                case LAB_MEMO: r.labMemo = v[0]; break;
            }
            return 0;
        } catch (IllegalArgumentException error) { return R.string.note_edit_invalid; }
    }
}
