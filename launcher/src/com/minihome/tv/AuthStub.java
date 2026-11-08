package com.minihome.tv;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.os.Bundle;

/** 假账号认证器: 只为挂周期同步(开机自愈通道), 不提供任何真实认证 */
public class AuthStub extends AbstractAccountAuthenticator {

    public AuthStub(Context c) {
        super(c);
    }

    public Bundle editProperties(AccountAuthenticatorResponse r, String type) {
        return null;
    }

    public Bundle addAccount(AccountAuthenticatorResponse r, String type, String tokenType,
            String[] features, Bundle options) {
        return null;
    }

    public Bundle confirmCredentials(AccountAuthenticatorResponse r, Account a, Bundle b) {
        return null;
    }

    public Bundle getAuthToken(AccountAuthenticatorResponse r, Account a, String tokenType, Bundle b)
            throws NetworkErrorException {
        return null;
    }

    public String getAuthTokenLabel(String tokenType) {
        return null;
    }

    public Bundle updateCredentials(AccountAuthenticatorResponse r, Account a, String tokenType, Bundle b) {
        return null;
    }

    public Bundle hasFeatures(AccountAuthenticatorResponse r, Account a, String[] features) {
        return null;
    }
}
