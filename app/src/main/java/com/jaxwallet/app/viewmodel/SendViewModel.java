package com.jaxwallet.app.viewmodel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.MutableLiveData;

import com.jaxwallet.app.C;
import com.jaxwallet.app.entity.AnalyticsProperties;
import com.jaxwallet.app.entity.NetworkInfo;
import com.jaxwallet.app.entity.SignAuthenticationCallback;
import com.jaxwallet.app.entity.TransactionData;
import com.jaxwallet.app.entity.Wallet;
import com.jaxwallet.app.entity.tokens.Token;
import com.jaxwallet.app.entity.tokens.TokenInfo;
import com.jaxwallet.app.interact.CreateTransactionInteract;
import com.jaxwallet.app.interact.FetchTransactionsInteract;
import com.jaxwallet.app.repository.EthereumNetworkRepositoryType;
import com.jaxwallet.app.repository.TokenRepository;
import com.jaxwallet.app.router.MyAddressRouter;
import com.jaxwallet.app.service.AnalyticsServiceType;
import com.jaxwallet.app.service.AssetDefinitionService;
import com.jaxwallet.app.service.GasService;
import com.jaxwallet.app.service.KeyService;
import com.jaxwallet.app.service.TokensService;
import com.jaxwallet.app.ui.ImportTokenActivity;
import com.jaxwallet.app.web3.entity.Web3Transaction;

import org.web3j.protocol.core.methods.response.EthEstimateGas;

import java.math.BigDecimal;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class SendViewModel extends BaseViewModel {
    private final MutableLiveData<Token> finalisedToken = new MutableLiveData<>();
    private final MutableLiveData<TransactionData> transactionFinalised = new MutableLiveData<>();
    private final MutableLiveData<Throwable> transactionError = new MutableLiveData<>();

    private final MyAddressRouter myAddressRouter;
    private final EthereumNetworkRepositoryType networkRepository;
    private final TokensService tokensService;
    private final FetchTransactionsInteract fetchTransactionsInteract;
    private final GasService gasService;
    private final AssetDefinitionService assetDefinitionService;
    private final KeyService keyService;
    private final CreateTransactionInteract createTransactionInteract;
    private final AnalyticsServiceType analyticsService;

    public SendViewModel(MyAddressRouter myAddressRouter,
                         EthereumNetworkRepositoryType ethereumNetworkRepositoryType,
                         TokensService tokensService,
                         FetchTransactionsInteract fetchTransactionsInteract,
                         CreateTransactionInteract createTransactionInteract,
                         GasService gasService,
                         AssetDefinitionService assetDefinitionService,
                         KeyService keyService,
                         AnalyticsServiceType analyticsService)
    {
        this.myAddressRouter = myAddressRouter;
        this.networkRepository = ethereumNetworkRepositoryType;
        this.tokensService = tokensService;
        this.fetchTransactionsInteract = fetchTransactionsInteract;
        this.gasService = gasService;
        this.assetDefinitionService = assetDefinitionService;
        this.keyService = keyService;
        this.createTransactionInteract = createTransactionInteract;
        this.analyticsService = analyticsService;
    }

    public MutableLiveData<TransactionData> transactionFinalised()
    {
        return transactionFinalised;
    }
    public MutableLiveData<Throwable> transactionError() { return transactionError; }

    public void showContractInfo(Context ctx, Wallet wallet, Token token)
    {
        myAddressRouter.open(ctx, wallet, token);
    }

    public NetworkInfo getNetworkInfo(long chainId)
    {
        return networkRepository.getNetworkByChain(chainId);
    }

    public Token getToken(long chainId, String tokenAddress) { return tokensService.getToken(chainId, tokenAddress); };

    public void showImportLink(Context context, String importTxt)
    {
        Intent intent = new Intent(context, ImportTokenActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(C.IMPORT_STRING, importTxt);
        context.startActivity(intent);
    }

    public void fetchToken(long chainId, String address, String walletAddress)
    {
        tokensService.update(address, chainId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(tokenInfo -> gotTokenUpdate(tokenInfo, walletAddress), this::onError).isDisposed();
    }

    private void gotTokenUpdate(TokenInfo tokenInfo, String walletAddress)
    {
        disposable = tokensService.addToken(tokenInfo, walletAddress)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(finalisedToken::postValue, this::onError);
    }

    public AssetDefinitionService getAssetDefinitionService()
    {
        return assetDefinitionService;
    }

    public TokensService getTokenService()
    {
        return tokensService;
    }

    public void startGasCycle(long chainId)
    {
        gasService.startGasPriceCycle(chainId);
    }

    public void onDestroy()
    {
        gasService.stopGasPriceCycle();
    }

    public byte[] getTransactionBytes(Token token, String sendAddress, BigDecimal sendAmount)
    {
        byte[] txBytes;
        if (token.isEthereum())
        {
            txBytes = new byte[0];
        }
        else
        {
            txBytes = TokenRepository.createTokenTransferData(sendAddress, sendAmount.toBigInteger());
        }

        return txBytes;
    }

    public Single<EthEstimateGas> calculateGasEstimate(Wallet wallet, byte[] transactionBytes, long chainId, String sendAddress, BigDecimal sendAmount)
    {
        return gasService.calculateGasEstimate(transactionBytes, chainId, sendAddress, sendAmount.toBigInteger(), wallet);
    }

    public void getAuthentication(Activity activity, Wallet wallet, SignAuthenticationCallback callback)
    {
        keyService.getAuthenticationForSignature(wallet, activity, callback);
    }

    public void sendTransaction(Web3Transaction finalTx, Wallet wallet, long chainId)
    {
        disposable = createTransactionInteract
                .createWithSig(wallet, finalTx, chainId)
                .subscribe(transactionFinalised::postValue,
                        transactionError::postValue);
    }

    public void actionSheetConfirm(String mode)
    {
        AnalyticsProperties analyticsProperties = new AnalyticsProperties();
        analyticsProperties.setData(mode);

        analyticsService.track(C.AN_CALL_ACTIONSHEET, analyticsProperties);
    }
}
