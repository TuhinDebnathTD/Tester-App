package com.example.practicingsomething

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MyViewModel : ViewModel() {
    val data = MutableLiveData<Data>()

    fun getData(): LiveData<Data> {
        return data
    }

    fun setData(data: Data) {
        this.data.value = data
    }
}