package net.kroia.stockmarket.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Calendar;

public class Timestamp implements ServerSaveable {

    int day;
    int month;
    int year;
    int hour;
    int minute;

    public Timestamp() {
        // Default constructor
        setTime(getCurrentTimeStamp());
    }
    public Timestamp(int day, int month, int year, int hour, int minute) {
        this.day = day;
        this.month = month;
        this.year = year;
        this.hour = hour;
        this.minute = minute;
    }

    public Timestamp(long timestamp) {
        // Convert the timestamp (in milliseconds) to the time variables
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);

        this.year = calendar.get(Calendar.YEAR);
        this.month = calendar.get(Calendar.MONTH) + 1;  // Calendar.MONTH is 0-based
        this.day = calendar.get(Calendar.DAY_OF_MONTH);
        this.hour = calendar.get(Calendar.HOUR_OF_DAY);
        this.minute = calendar.get(Calendar.MINUTE);
    }

    public static long getCurrentTimeStamp() {
        // Get the current time in milliseconds
        return Calendar.getInstance().getTimeInMillis();
    }
    public long getTimeStamp() {
        // Combine the time variables to a single long value (timestamp in milliseconds)
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month - 1, day, hour, minute, 0);  // Calendar.MONTH is 0-based
        return calendar.getTimeInMillis();
    }

    public String getTimeStampString() {
        return String.format("%02d/%02d/%04d %02d:%02d", day, month, year, hour, minute);
    }

    public String getTimeStampString(int timestamp) {
        // Convert the timestamp to a readable string
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        return String.format("%02d/%02d/%04d %02d:%02d", calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.YEAR), calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
    }

    public void setTime(int day, int month, int year, int hour, int minute) {
        this.day = day;
        this.month = month;
        this.year = year;
        this.hour = hour;
        this.minute = minute;
    }
    public void setTime(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);

        this.year = calendar.get(Calendar.YEAR);
        this.month = calendar.get(Calendar.MONTH) + 1;  // Calendar.MONTH is 0-based
        this.day = calendar.get(Calendar.DAY_OF_MONTH);
        this.hour = calendar.get(Calendar.HOUR_OF_DAY);
        this.minute = calendar.get(Calendar.MINUTE);
    }

    // Interface to send the timestamp over the network
    public Timestamp(FriendlyByteBuf buf) {
        this.day = buf.readInt();
        this.month = buf.readInt();
        this.year = buf.readInt();
        this.hour = buf.readInt();
        this.minute = buf.readInt();
    }

    public static Timestamp loadFromTag(CompoundTag tag) {
        Timestamp timestamp = new Timestamp();
        if(timestamp.load(tag))
            return timestamp;
        return null;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(day);
        buf.writeInt(month);
        buf.writeInt(year);
        buf.writeInt(hour);
        buf.writeInt(minute);
    }

    @Override
    public boolean save(CompoundTag tag) {
        tag.putInt("day", day);
        tag.putInt("month", month);
        tag.putInt("year", year);
        tag.putInt("hour", hour);
        tag.putInt("minute", minute);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;
        if(     !tag.contains("day") ||
                !tag.contains("month") ||
                !tag.contains("year") ||
                !tag.contains("hour") ||
                !tag.contains("minute"))
            return false;
        day = tag.getInt("day");
        month = tag.getInt("month");
        year = tag.getInt("year");
        hour = tag.getInt("hour");
        minute = tag.getInt("minute");
        return true;
    }
}
