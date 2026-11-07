package net.kroia.stockmarket.networking.request;


import net.kroia.banksystem.api.bank.BankStatus;
import net.kroia.banksystem.api.bank.IServerBank;
import net.kroia.banksystem.api.bankaccount.ISyncServerBankAccount;
import net.kroia.banksystem.api.bankmanager.IBankManager;
import net.kroia.banksystem.banking.BankPermission;
import net.kroia.banksystem.banking.User;
import net.kroia.banksystem.banking.clientdata.BankUserData;
import net.kroia.banksystem.banking.clientdata.UserData;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.networking.NetworkGate;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CreateOrderRequest extends StockMarketGenericRequest<CreateOrderRequest.InputData, CreateOrderRequest.OutputData> {

    public record InputData(ItemID itemID, int bankAccountNr, Order.Type type, double volume, double price)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, CreateOrderRequest.InputData> STREAM_CODEC = StreamCodec.composite(
                ItemID.STREAM_CODEC, p -> p.itemID,
                ByteBufCodecs.INT, p -> p.bankAccountNr,
                ExtraCodecUtils.enumStreamCodec(Order.Type.class), p -> p.type,
                ByteBufCodecs.DOUBLE, p -> p.volume,
                ByteBufCodecs.DOUBLE, p -> p.price,
                InputData::new
        );
    }

    public enum Status
    {
        CREATED,
        NO_SUCH_MARKET,
        INVALID_VOLUME,
        INVALID_PRICE,
        NO_SERVER_BANK_MANAGER, // should never occur
        NO_BANK_ACCOUNT,
        NO_BANK_USER,
        NO_ITEM_BANK,
        NO_MONEY_BANK,
        NO_PERMISSION_TO_DEPOSIT,
        NO_PERMISSION_TO_WITHDRAW,
        NOT_ENOUGH_MONEY,
        NOT_ENOUGH_ITEM,
        UNABLE_TO_LOCK_MONEY,
        UNABLE_TO_LOCK_ITEM,
        UNABLE_TO_UNLOCK_MONEY,
        UNABLE_TO_UNLOCK_ITEM,
        NO_PLAYER_SENDER,
        // Appended (ordinal is the wire format): market is under maintenance or outside its trading hours.
        MARKET_CLOSED,
        // Appended (T-166): |volume| exceeds the market's maxOrderVolume. OutputData.detail = the limit.
        ORDER_TOO_LARGE,
        // Appended (T-166): the player's global trade cooldown has not elapsed. OutputData.detail = remaining ms.
        TRADING_TOO_FAST
    }
    public static class OutputData
    {
        public Status status;
        public @Nullable Order orderEcho;
        /**
         * Extra number for the client message: the max order volume for {@link Status#ORDER_TOO_LARGE},
         * the remaining cooldown in ms for {@link Status#TRADING_TOO_FAST}, otherwise 0.
         */
        public long detail;
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.composite(
                ExtraCodecUtils.enumStreamCodec(Status.class), p -> p.status,
                ExtraCodecUtils.nullable(Order.STREAM_CODEC), p -> p.orderEcho,
                ByteBufCodecs.VAR_LONG, p -> p.detail,
                OutputData::new
        );
        public OutputData(Status status, @Nullable Order orderEcho)
        {
            this(status, orderEcho, 0);
        }
        /**
         * @param status    result status
         * @param orderEcho the created order, or null
         * @param detail    extra number for the client message (see {@link #detail})
         */
        public OutputData(Status status, @Nullable Order orderEcho, long detail)
        {
            this.status = status;
            this.orderEcho = orderEcho;
            this.detail = detail;
        }
    }

    @Override
    public String getRequestTypeID() {
        return CreateOrderRequest.class.getName();
    }

    @Override
    protected OutputData getDefaultResponse() {
        return new OutputData(Status.NO_SERVER_BANK_MANAGER, null);
    }

    @Override
    public CompletableFuture<OutputData> handleOnMasterServer(CreateOrderRequest.InputData input, String slaveID, @Nullable UUID playerSender) {
        if(playerSender == null || (needsRoutingToMaster() && !MultiServerUtils.canInteractWithStockMarket(playerSender)))
            return CompletableFuture.completedFuture(new OutputData(Status.NO_PLAYER_SENDER, null));
        // T-123 (untrusted slave gate): mutating call — reject if forwarded by
        // an untrusted slave. Returns the mod's existing "no player sender"
        // failure code (this Request type's Status enum was not extended for
        // T-123 — the client already knows it's on an untrusted slave via
        // ClientSettings.isSlaveTrusted() and disables the buy/sell buttons;
        // the server-side rejection here only fires against a modded client
        // that bypassed the button gate). The gate helper logs a WARN.
        if (!NetworkGate.isMutatingCallAllowed(slaveID, "CreateOrderRequest"))
            return CompletableFuture.completedFuture(new OutputData(Status.NO_PLAYER_SENDER, null));

        CompletableFuture<OutputData> future = new CompletableFuture<>();
        OutputData response = new OutputData(Status.CREATED, null);
         IServerMarket serverMarket = getServerMarketManager().getMarket(input.itemID);
         if(serverMarket == null || input.volume == 0 || input.price < 0)
         {
             if(serverMarket == null)
                 response.status = Status.NO_SUCH_MARKET;
             if(input.volume == 0)
                 response.status = Status.INVALID_VOLUME;
             if(input.price < 0)
                 response.status = Status.INVALID_PRICE;
             future.complete(response);
             return future;
         }
         // Under maintenance or outside trading hours: reject before any funds are locked.
         // putOrder() would refuse the order anyway; checking here avoids a
         // pointless lock/unlock round-trip and gives the player a clear status.
         if(serverMarket.getTradingState() != MarketSettings.TradingState.OPEN)
         {
             response.status = Status.MARKET_CLOSED;
             future.complete(response);
             return future;
         }
         // T-166 anti-autoclicker: per-market max order size, then the global per-player cooldown.
         // Exempt: a limit order covered by a server-granted move credit (the player just had the
         // master cancel an own limit order of at least this volume, i.e. a chart move or its restore).
         // Not client-settable — see ServerMarketManager.MoveCredit.
         ServerMarketManager marketManager = (ServerMarketManager) getServerMarketManager();
         ServerMarketManager.MoveCredit moveCredit = input.type == Order.Type.LIMIT
                 ? marketManager.findMoveCredit(playerSender, input.itemID, null, toRawAmount(input.volume)) : null;
         if(moveCredit == null)
         {
             long maxOrderVolume = serverMarket.getSettings().maxOrderVolume;
             if(maxOrderVolume > 0 && Math.abs(input.volume) > maxOrderVolume)
             {
                 future.complete(new OutputData(Status.ORDER_TOO_LARGE, null, maxOrderVolume));
                 return future;
             }
             long cooldownRemaining = marketManager.getTradeCooldownRemainingMs(playerSender);
             if(cooldownRemaining > 0)
             {
                 future.complete(new OutputData(Status.TRADING_TOO_FAST, null, cooldownRemaining));
                 return future;
             }
         }

        IBankManager serverBankManager = getServerBankManager();
        if(serverBankManager == null) {
            error("No IServerBankManager found ");
            response.status = Status.NO_SERVER_BANK_MANAGER;
            future.complete(response);
            return future;
        }

        ISyncServerBankAccount bankAccount = serverBankManager.getSync().getBankAccount(input.bankAccountNr);
        if(bankAccount == null) {
            warn("No BankAccount found with BankAccountNr " + input.bankAccountNr);
            response.status = Status.NO_BANK_ACCOUNT;
            future.complete(response);
            return future;
        }

        // T-131 (security): verify the requesting player is actually a
        // member/owner of the target account AND holds the required permissions
        // BEFORE locking any funds. Without it a crafted client could pass an
        // arbitrary bankAccountNr and lock/trade an account it does not own or
        // lacks permission on.
        //
        // NOTE: getUserData(...) deliberately excludes the personal bank owner
        // (see ISyncServerBankAccount#getUserData Javadoc). Members are checked via
        // their BankUserData permissions; the personal owner is checked via
        // getPersonalBankOwnerData() and implicitly holds all permissions.
        BankUserData bankUserData = bankAccount.getUserData(playerSender);
        if(bankUserData == null) {
            UserData userData = bankAccount.getPersonalBankOwnerData();
            // Only the actual personal owner may use the account without an explicit
            // member entry. We also verify the owner UUID matches the sender:
            // getUserData() excludes the owner, so a non-member targeting someone
            // else's personal account would otherwise slip through and let a crafted
            // client trade on an account it does not own.
            if(userData == null || !userData.userUUID().equals(playerSender)) {
                User us = getServerBankManager().getSync().getUserByUUID(playerSender);
                String userName;
                if(us != null)
                    userName = us.getName();
                else
                    userName = playerSender.toString();
                warn("No BankUserData found with BankAccountNr " + input.bankAccountNr + " for user: " +userName+
                        "\nThis player seems not to be a member of the BankAccount.");
                response.status = Status.NO_BANK_USER;
                future.complete(response);
                return future;
            }
            // Personal owner — implicitly holds all permissions, allow.
        }
        else {
            // Member: require BOTH WITHDRAW and DEPOSIT regardless of order
            // direction. A buy withdraws money and deposits items; a sell
            // withdraws items and deposits money — both directions touch both
            // operations, so both permissions are required. This also makes an
            // account "tradeable iff the player holds both perms", consistent with
            // the TradeScreen account-selector filter.
            if (!BankPermission.hasPermission(bankUserData.permissions, BankPermission.WITHDRAW)) {
                response.status = Status.NO_PERMISSION_TO_WITHDRAW;
                future.complete(response);
                return future; // User is not allowed to withdraw
            }
            if (!BankPermission.hasPermission(bankUserData.permissions, BankPermission.DEPOSIT)) {
                response.status = Status.NO_PERMISSION_TO_DEPOSIT;
                future.complete(response);
                return future; // User is not allowed to deposit
            }
        }

        if(input.type == Order.Type.INTER_MARKET) {
            response.status = Status.NO_SUCH_MARKET;
            future.complete(response);
            return future;
        }

        // Check balance
        IServerBank itemBank = bankAccount.getBank(input.itemID);
        if(itemBank == null) {
            if(input.volume < 0) {
                response.status = Status.NO_ITEM_BANK;
                future.complete(response);
                return future;
            }
            else
            {
                itemBank = bankAccount.createBank(input.itemID, 0);
            }
        }

        ItemID moneyItemID = getServerMarketManager().getTradingCurrencyID();
        IServerBank moneyBank = bankAccount.getBank(moneyItemID);
        if(moneyBank == null)
        {
            // T-131 (Bug A fix): a non-personal trading account (e.g. a shared company
            // account created to hold a single item) may never have held the trading
            // currency, so it has no money bank yet. The personal account always has
            // one — which is why previously ONLY the personal account could place
            // orders and non-personal accounts silently failed with NO_MONEY_BANK.
            // Mirror the item-bank auto-creation above and create an empty money bank
            // so the order can proceed: a SELL deposits its proceeds here, while a BUY
            // still (correctly) hits the NOT_ENOUGH_MONEY balance check below because
            // the freshly created bank has a zero balance. The MatchingEngine also
            // requires this bank to exist (it cancels any order whose money bank is
            // missing), so creating it here upholds that invariant.
            // NOTE: this does NOT bypass any permission gate — the membership +
            // DEPOSIT/WITHDRAW checks above already ran and rejected unauthorized users.
            moneyBank = bankAccount.createBank(moneyItemID, 0);
            if(moneyBank == null)
            {
                response.status = Status.NO_MONEY_BANK;
                future.complete(response);
                return future;
            }
        }

        long currentMarketPrice = serverMarket.getCurrentMarketPrice();
        long toLockAmount = 0;
        if(input.volume > 0)
        {
            // Buy, check money balance
            switch(input.type)
            {
                case Order.Type.LIMIT:
                    // Correct cost: real volume * real price * scaleFactor = raw cost
                    // Use ceil to ensure lock amount always covers the full order cost
                    toLockAmount = (long)Math.ceil(input.volume * input.price * getItemFractionScaleFactor());
                    if(moneyBank.getBalance() < toLockAmount) {
                        response.status = Status.NOT_ENOUGH_MONEY;
                        future.complete(response);
                        return future;
                    }
                    break;
                case Order.Type.MARKET:
                    // Correct cost: rawVolume * rawPrice / scaleFactor = raw cost
                    // Use ceil to ensure lock amount always covers the full order cost
                    toLockAmount = (long)Math.ceil((double)toRawAmount(input.volume) * currentMarketPrice / getItemFractionScaleFactor());
                    if(moneyBank.getBalance() < toLockAmount) {
                        response.status = Status.NOT_ENOUGH_MONEY;
                        future.complete(response);
                        return future;
                    }
                    break;
            }
        }
        else
        {
            switch(input.type)
            {
                case Order.Type.LIMIT:
                case Order.Type.MARKET:
                    toLockAmount = toRawAmount(-input.volume);
                    if(itemBank.getBalance() < toLockAmount) {
                        response.status = Status.NOT_ENOUGH_ITEM;
                        future.complete(response);
                        return future;
                    }
                break;
            }
        }

        // Reserve balances
        if(input.volume > 0)
        {
            BankStatus lockStatus = moneyBank.lockAmount(toLockAmount);
            if(lockStatus != BankStatus.SUCCESS)
            {
                warn("Trying to lock "+toLockAmount+" of "+ moneyItemID + " for bank: " +moneyBank +
                        " of BankAccount: "+ bankAccount.getAccountNumber() + "["+bankAccount.getAccountName()+"]. Got status: "+lockStatus);
                response.status = Status.UNABLE_TO_LOCK_MONEY;
                future.complete(response);
                return future;
            }
            // Stash the actual reservation on the Order — market buys have startPrice=0
            // so the "targetVolume * startPrice / SF" formula in unlockRemainingFunds
            // cannot back-compute this. Set BEFORE Order is created below via a two-step
            // pattern (create then set), since the Order construction happens later.
        }
        else
        {
            BankStatus lockStatus = itemBank.lockAmount(toLockAmount);
            if(lockStatus != BankStatus.SUCCESS)
            {
                warn("Trying to lock "+toLockAmount+" of "+ input.itemID + " for bank: " +itemBank +
                        " of BankAccount: "+ bankAccount.getAccountNumber() + "["+bankAccount.getAccountName()+"]. Got status: "+lockStatus);
                response.status = Status.UNABLE_TO_LOCK_ITEM;
                future.complete(response);
                return future;
            }
        }

        long time = System.currentTimeMillis();
        Order order = new Order(input.itemID, input.type, toRawAmount(input.volume), toRawAmount(input.price), time,
                playerSender, bankAccount.getAccountNumber());
        // Buy orders: record the actual money reservation on the Order so the
        // cancel-remainder refund path can back-compute the unlock amount for market
        // buys (whose startPrice is 0). Sell orders don't need this — unlock uses
        // -remainingVolume directly.
        if (input.volume > 0) {
            order.setOriginalLockedMoney(toLockAmount);
        }

        if(!serverMarket.putOrder(order))
        {
            if(input.volume > 0) {
                BankStatus unlockStatus = moneyBank.unlockAmount(toLockAmount);
                if(unlockStatus != BankStatus.SUCCESS)
                {
                    warn("Trying to unlock "+toLockAmount+" of "+ moneyItemID + " for bank: " +moneyBank +
                            " of BankAccount: "+ bankAccount.getAccountNumber() + "["+bankAccount.getAccountName()+"] " +
                            "after failed to place order. Got status: "+unlockStatus);
                    response.status = Status.UNABLE_TO_UNLOCK_MONEY;
                    future.complete(response);
                    return future;
                }
            }
            else
            {
                BankStatus unlockStatus = itemBank.unlockAmount(toLockAmount);
                if(unlockStatus != BankStatus.SUCCESS)
                {
                    warn("Trying to unlock "+toLockAmount+" of "+ input.itemID + " for bank: " +itemBank +
                            " of BankAccount: "+ bankAccount.getAccountNumber() + "["+bankAccount.getAccountName()+"] " +
                            "after failed to place order. Got status: "+unlockStatus);
                    response.status = Status.UNABLE_TO_UNLOCK_ITEM;
                    future.complete(response);
                    return future;
                }
            }
            // Funds are released — report the rejection instead of CREATED.
            // A maintenance/close can race the early check above; any other putOrder()
            // refusal (e.g. the volume rounded to 0 raw units) is a volume problem.
            response.status = serverMarket.getTradingState() != MarketSettings.TradingState.OPEN
                    ? Status.MARKET_CLOSED : Status.INVALID_VOLUME;
            future.complete(response);
            return future;
        }
        // Only accepted orders (re)start the player's trade cooldown; a re-placed (moved) order
        // instead uses up its move credit and does not count as a new trade.
        if(moveCredit != null)
            marketManager.consumeMoveCredit(playerSender, moveCredit);
        else
            marketManager.markPlayerOrderAccepted(playerSender);
        response.status = Status.CREATED;
        response.orderEcho = order;
        future.complete(response);
        return future;
    }

    private long toRawAmount(double realAmount)
    {
        return getServerBankManager().getSync().convertToRawAmount(realAmount);
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, InputData input) {
        InputData.STREAM_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, OutputData output) {
        OutputData.STREAM_CODEC.encode(buf, output);
    }

    @Override
    public InputData decodeInput(RegistryFriendlyByteBuf buf) {
        return InputData.STREAM_CODEC.decode(buf);
    }

    @Override
    public OutputData decodeOutput(RegistryFriendlyByteBuf buf) {
        return OutputData.STREAM_CODEC.decode(buf);
    }


}
