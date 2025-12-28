package com.theblankstate.libri.states

import com.theblankstate.libri.datamodel.bookModel

sealed class state {

    object  loading : state()
    data class success(val data:List<bookModel>) : state()
    data class error(val message:String) : state()


}