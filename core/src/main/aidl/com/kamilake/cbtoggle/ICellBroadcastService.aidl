package com.kamilake.cbtoggle;

import com.kamilake.cbtoggle.UserState;

interface ICellBroadcastService {
    // Transaction code reserved by Shizuku for stopping the service.
    void destroy() = 16777114;

    List<UserState> query() = 1;

    void setEnabled(boolean enabled) = 2;

    void watchClient(IBinder client) = 3;
}
