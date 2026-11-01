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
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Request to place an inter-market order.
 * An inter-market order sells items from one market and uses the proceeds to buy items on another market.
 * The player specifies which item they have (sell) and which item they want (buy),
 * along with the volume of "have" items to sell and an optional cross-rate limit.
 */
public class PlaceInterMarketOrderRequest extends StockMarketGenericRequest<PlaceInterMarketOrderRequest.InputData, PlaceInterMarketOrderRequest.OutputData> {

    /**
     * Input data for placing an inter-market order.
     *
     * @param haveItemID    the item the player is selling
     * @param wantItemID    the item the player is buying
     * @param bankAccountNr the player's bank account number
     * @param volume        amount of "have" items to sell (positive, real format)
     * @param crossRateLimit rate limit in raw format (0 = market order)
     */
    public record InputData(ItemID haveItemID, ItemID wantItemID, int bankAccountNr, double volume, long crossRateLimit, long targetBuyVolume) {
        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.composite(
                ItemID.STREAM_CODEC, p -> p.haveItemID,
                ItemID.STREAM_CODEC, p -> p.wantItemID,
                ByteBufCodecs.INT, p -> p.bankAccountNr,
                ByteBufCodecs.DOUBLE, p -> p.volume,
                ByteBufCodecs.VAR_LONG, p -> p.crossRateLimit,
                ByteBufCodecs.VAR_LONG, p -> p.targetBuyVolume,
                InputData::new
        );
    }

    /**
     * Output data indicating success or failure of placing the inter-market order.
     */
    public static class OutputData {
        public boolean success;
        public @Nullable String errorMessage;

        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public void encode(RegistryFriendlyByteBuf buf, OutputData data) {
                ByteBufCodecs.BOOL.encode(buf, data.success);
                ExtraCodecUtils.nullable(ByteBufCodecs.STRING_UTF8).encode(buf, data.errorMessage);
            }

            @Override
            public OutputData decode(RegistryFriendlyByteBuf buf) {
                boolean success = ByteBufCodecs.BOOL.decode(buf);
                String errorMessage = ExtraCodecUtils.nullable(ByteBufCodecs.STRING_UTF8).decode(buf);
                return new OutputData(success, errorMessage);
            }
        };

        public OutputData(boolean success, @Nullable String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
    }

    @Override
    public String getRequestTypeID() {
        return PlaceInterMarketOrderRequest.class.getName();
    }

    @Override
    protected OutputData getDefaultResponse() {
        return new OutputData(false, "NO_SERVER");
    }

    @Override
    public CompletableFuture<OutputData> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
        // 1. Check playerSender not null
        if (playerSender == null || (needsRoutingToMaster() && !MultiServerUtils.canInteractWithStockMarket(playerSender)))
            return CompletableFuture.completedFuture(new OutputData(false, "NO_PLAYER_SENDER"));
        // T-123 (untrusted slave gate): mutating call — reject if forwarded by
        // an untrusted slave. errorMessage tags the reason so admins reading
        // server logs / debug UIs can see the cause; the client-side UX is
        // driven independently from ClientSettings.isSlaveTrusted().
        if (!NetworkGate.isMutatingCallAllowed(slaveID, "PlaceInterMarketOrderRequest"))
            return CompletableFuture.completedFuture(new OutputData(false, "UNTRUSTED_SLAVE"));

        CompletableFuture<OutputData> future = new CompletableFuture<>();

        // 2. Validate volume is positive
        if (input.volume <= 0) {
            future.complete(new OutputData(false, "INVALID_VOLUME"));
            return future;
        }

        // 3. Validate both markets exist and are open
        IServerMarket haveMarket = getServerMarketManager().getMarket(input.haveItemID);
        if (haveMarket == null) {
            future.complete(new OutputData(false, "NO_SUCH_MARKET_HAVE"));
            return future;
        }
        if (!haveMarket.isMarketOpen()) {
            future.complete(new OutputData(false, "MARKET_CLOSED_HAVE"));
            return future;
        }

        IServerMarket wantMarket = getServerMarketManager().getMarket(input.wantItemID);
        if (wantMarket == null) {
            future.complete(new OutputData(false, "NO_SUCH_MARKET_WANT"));
            return future;
        }
        if (!wantMarket.isMarketOpen()) {
            future.complete(new OutputData(false, "MARKET_CLOSED_WANT"));
            return future;
        }

        // 4. Get bank manager and bank account
        IBankManager serverBankManager = getServerBankManager();
        if (serverBankManager == null) {
            error("No IServerBankManager found");
            future.complete(new OutputData(false, "NO_SERVER_BANK_MANAGER"));
            return future;
        }

        ISyncServerBankAccount bankAccount = serverBankManager.getSync().getBankAccount(input.bankAccountNr);
        if (bankAccount == null) {
            warn("No BankAccount found with BankAccountNr " + input.bankAccountNr);
            future.complete(new OutputData(false, "NO_BANK_ACCOUNT"));
            return future;
        }

        // 4b. T-131 (security): verify the requesting player is actually a
        // member/owner of the target account AND holds the required permissions
        // BEFORE locking any funds. This mirrors CreateOrderRequest's
        // membership+permission gate (which only checked account existence here).
        // Without it a crafted client could pass an arbitrary bankAccountNr and
        // lock/trade an account it does not own or lacks permission on.
        //
        // An inter-market order withdraws the "have" items (to sell) and deposits
        // the acquired "want" items, so BOTH WITHDRAW and DEPOSIT are required.
        //
        // This is orthogonal to the T-123 NetworkGate above (slave trust) and the
        // T-130 admin gates — those govern who may forward/manage; this governs
        // whether the sender may act on the ORDER's target account.
        // NOTE: getUserData(...) deliberately excludes the personal bank owner
        // (see ISyncServerBankAccount#getUserData Javadoc). Members are checked via
        // their BankUserData permissions; the personal owner is checked via
        // getPersonalBankOwnerData() and implicitly holds all permissions.
        BankUserData bankUserData = bankAccount.getUserData(playerSender);
        if (bankUserData == null) {
            UserData ownerData = bankAccount.getPersonalBankOwnerData();
            // Only the actual personal owner may use the account without an explicit
            // member entry. Unlike CreateOrderRequest we also verify the owner UUID
            // matches the sender: getUserData() excludes the owner, so a non-member
            // targeting someone else's personal account would otherwise slip through.
            if (ownerData == null || !ownerData.userUUID().equals(playerSender)) {
                User us = serverBankManager.getSync().getUserByUUID(playerSender);
                String userName = (us != null) ? us.getName() : playerSender.toString();
                warn("No BankUserData found with BankAccountNr " + input.bankAccountNr + " for user: " + userName +
                        "\nThis player seems not to be a member of the BankAccount.");
                future.complete(new OutputData(false, "NO_BANK_USER"));
                return future;
            }
            // Personal owner — implicitly holds all permissions, allow.
        } else {
            // Member: require WITHDRAW (to sell the "have" items) and DEPOSIT
            // (to receive the acquired "want" items).
            if (!BankPermission.hasPermission(bankUserData.permissions, BankPermission.WITHDRAW)) {
                future.complete(new OutputData(false, "NO_PERMISSION_TO_WITHDRAW"));
                return future;
            }
            if (!BankPermission.hasPermission(bankUserData.permissions, BankPermission.DEPOSIT)) {
                future.complete(new OutputData(false, "NO_PERMISSION_TO_DEPOSIT"));
                return future;
            }
        }

        // 5. Validate player has the "have" items in their item bank
        IServerBank itemBank = bankAccount.getBank(input.haveItemID);
        if (itemBank == null) {
            future.complete(new OutputData(false, "NO_ITEM_BANK"));
            return future;
        }

        long rawVolume = toRawAmount(input.volume);
        if (rawVolume <= 0) {
            future.complete(new OutputData(false, "INVALID_VOLUME"));
            return future;
        }

        // For buy-direction market orders, add a margin to the sell volume to compensate
        // for spread and rounding losses. The execution only sells what's needed;
        // any unused locked items are unlocked after the order completes.
        // Sell-direction orders (targetBuyVolume == 0) use the exact volume the user specified.
        long lockVolume = rawVolume;
        if (input.crossRateLimit == 0 && input.targetBuyVolume > 0) {
            long margin = Math.max(rawVolume / 20, 1);
            lockVolume = Math.min(rawVolume + margin, itemBank.getBalance());
            if (lockVolume < rawVolume) lockVolume = rawVolume;
        }

        if (itemBank.getBalance() < lockVolume) {
            future.complete(new OutputData(false, "NOT_ENOUGH_ITEMS"));
            return future;
        }

        // 6. Lock player's "have" items in the item bank
        BankStatus lockStatus = itemBank.lockAmount(lockVolume);
        if (lockStatus != BankStatus.SUCCESS) {
            warn("Trying to lock " + lockVolume + " of " + input.haveItemID + " for bank: " + itemBank +
                    " of BankAccount: " + bankAccount.getAccountNumber() + "[" + bankAccount.getAccountName() + "]. Got status: " + lockStatus);
            future.complete(new OutputData(false, "UNABLE_TO_LOCK_ITEMS"));
            return future;
        }

        // 7. Build the InterMarketOrder
        long time = System.currentTimeMillis();
        long SF = getItemFractionScaleFactor();
        long havePrice = haveMarket.getCurrentMarketPrice();
        long wantPrice = wantMarket.getCurrentMarketPrice();

        // crossRateLimit == 0 means market order, otherwise limit order
        Order.Type orderType = (input.crossRateLimit == 0) ? Order.Type.MARKET : Order.Type.LIMIT;

        // For limit orders, derive the intended buy quantity from the ORIGINAL volume
        // (without margin) and rate limit. The margin is extra sell capacity, not extra buy target.
        long estimatedBuyVolume;
        if (input.targetBuyVolume > 0) {
            // Buy-direction: exact target from client
            estimatedBuyVolume = input.targetBuyVolume;
        } else if (input.crossRateLimit > 0) {
            // Sell-direction limit order: estimate for partial-fill tracking
            estimatedBuyVolume = Math.round((double)(rawVolume * SF) / input.crossRateLimit);
        } else {
            // Sell-direction market order: no explicit buy target.
            // Use 0 to signal sell-direction to the execution layer.
            // Market orders always return FILLED regardless of isFilled().
            estimatedBuyVolume = 0;
        }
        if (estimatedBuyVolume <= 0 && input.crossRateLimit > 0) estimatedBuyVolume = 1;

        InterMarketOrder imo = new InterMarketOrder(
                input.wantItemID, input.haveItemID,  // buyItemID = want, sellItemID = have
                orderType,
                estimatedBuyVolume, wantPrice,       // buy leg: target quantity unchanged
                lockVolume, havePrice,               // sell leg: includes margin for market orders
                time, playerSender, input.bankAccountNr,
                input.crossRateLimit
        );

        // 8. Enqueue the inter-market order on the "want" (buy) market
        ServerMarketManager smm = (ServerMarketManager) getServerMarketManager();
        if (!smm.putInterMarketOrder(imo)) {
            // Enqueue failed — unlock the items
            BankStatus unlockStatus = itemBank.unlockAmount(lockVolume);
            if (unlockStatus != BankStatus.SUCCESS) {
                warn("Trying to unlock " + lockVolume + " of " + input.haveItemID + " for bank: " + itemBank +
                        " of BankAccount: " + bankAccount.getAccountNumber() + "[" + bankAccount.getAccountName() + "] " +
                        "after failed to enqueue inter-market order. Got status: " + unlockStatus);
            }
            future.complete(new OutputData(false, "ENQUEUE_FAILED"));
            return future;
        }

        // 9. Success
        future.complete(new OutputData(true, null));
        return future;
    }

    /**
     * Converts a real (human-readable) amount to the raw backend value.
     */
    private long toRawAmount(double realAmount) {
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
