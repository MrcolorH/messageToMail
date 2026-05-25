package com.example.messagetomail.model

import androidx.databinding.ObservableField
import androidx.lifecycle.ViewModel;

class LogViewModel : ViewModel() {
    // 用于存储手机号
    val phoneNumber = ObservableField<String>()

    // 用于存储内容
    val content = ObservableField<String>("服务停用")

    // 用于存储报错信息
    val errorMessage = ObservableField<String>()

    //服务状态，先放着用下
    val serviceState = ObservableField<Int>(0)

    fun setContent(newContent: String) {
        content.set(newContent) // 修改值时使用set方法
    }

    fun setServiceState(newContent: Int){
        serviceState.set(newContent)
    }
    fun setPhoneNumber(newContent: String) {
        phoneNumber.set(newContent)
    }

    fun setErrorMessage(newContent: String) {
        errorMessage.set(newContent)
    }
}
