package com.example.testapp.model

import android.os.Parcel
import android.os.Parcelable

data class User(
    val retirementHomeID: String,
    val ipaddress: String,
    val port: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(retirementHomeID)
        parcel.writeString(ipaddress)  // Write nullable fields as-is
        parcel.writeString(port)       // Write nullable fields as-is
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<User> {
        override fun createFromParcel(parcel: Parcel): User {
            return User(parcel)
        }

        override fun newArray(size: Int): Array<User?> {
            return arrayOfNulls(size)
        }
    }
}