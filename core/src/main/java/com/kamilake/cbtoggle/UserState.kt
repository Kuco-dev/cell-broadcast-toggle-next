package com.kamilake.cbtoggle

import android.os.Parcel
import android.os.Parcelable

const val STATE_NOT_INSTALLED = -1
const val STATE_DISABLED = 0
const val STATE_ENABLED = 1

class UserState(
    val userId: Int,
    val name: String,
    val accessible: Boolean,
    /** One entry per [TARGET_PACKAGES] item. */
    val states: IntArray,
) : Parcelable {

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(userId)
        dest.writeString(name)
        dest.writeBoolean(accessible)
        dest.writeIntArray(states)
    }

    companion object CREATOR : Parcelable.Creator<UserState> {
        override fun createFromParcel(source: Parcel) = UserState(
            userId = source.readInt(),
            name = source.readString().orEmpty(),
            accessible = source.readBoolean(),
            states = source.createIntArray() ?: IntArray(0),
        )

        override fun newArray(size: Int) = arrayOfNulls<UserState>(size)
    }
}
