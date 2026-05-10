package net.kroia.stockmarket.networking.request;

import net.kroia.banksystem.api.bankaccount.ISyncServerBankAccount;
import net.kroia.banksystem.api.bankmanager.IBankManager;
import net.kroia.banksystem.banking.clientdata.BankUserData;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * @brief
 * Requests for pending orders which are waiting in the order book.
 * Non-admin players only receive orders that belong to bank accounts they are a member of.
 *
 * todo: Add InterMarketOrders to this packet
 */
public class ActiveOrdersRequest extends StockMarketGenericRequest<ActiveOrdersRequest.InputData, ActiveOrdersRequest.OutputData>
{

    /**
     * The InputData is used as query filter
     * @param itemID If set, only orders associated with the given item are requested.
     *               If null, all items are included
     * @param bankAccountNr Only orders that are associated with the given bankAccount are requested.
     *                      If set to a value < 0, all bankAccounts are included
     * @param executorPlayer If set, only orders that were created by the given user are requested.
     *                       If set to null, orders from all players are requested.
     * @param timeBegin Start of the time filter range
     * @param timeEnd   End of the time filter range
     *                  All order in between this range are requested.
     */
    public record InputData(@Nullable ItemID itemID, int bankAccountNr, @Nullable UUID executorPlayer, long timeBegin, long timeEnd)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.composite(
                ExtraCodecUtils.nullable(ItemID.STREAM_CODEC), p -> p.itemID,
                ByteBufCodecs.INT, p -> p.bankAccountNr,
                ExtraCodecUtils.nullable(UUIDUtil.STREAM_CODEC), p -> p.executorPlayer,
                ByteBufCodecs.VAR_LONG, p -> p.timeBegin,
                ByteBufCodecs.VAR_LONG, p -> p.timeEnd,
                InputData::new
        );
    }

    public record OutputData(List<Order> orders)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.composite(
                ExtraCodecUtils.listStreamCodec(Order.STREAM_CODEC), p -> p.orders,
                OutputData::new
        );
    }



    @Override
    public String getRequestTypeID() {
        return ActiveOrdersRequest.class.getName();
    }

    @Override
    protected OutputData getDefaultResponse() {
        return new OutputData(List.of());
    }

    @Override
    public CompletableFuture<OutputData> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
        if(playerSender == null || (needsRoutingToMaster() && !MultiServerUtils.canInteractWithStockMarket(playerSender)))
            return CompletableFuture.completedFuture(new OutputData(List.of()));
        CompletableFuture<OutputData>  future = new CompletableFuture<>();
        List<Order> orderData = new ArrayList<>();

        boolean isAdmin = playerIsAdmin(playerSender);
        IBankManager bankManager = getServerBankManager();
        Map<Integer, Boolean> bankAccountMembershipCache = new HashMap<>();

        IServerMarketManager serverMarketManager = getServerMarketManager();
        if(input.itemID != null)
        {
            IServerMarket serverMarket = serverMarketManager.getMarket(input.itemID);
            if(serverMarket == null) {
                future.complete(new OutputData(orderData));
                return future;
            }
            List<Order> orders = serverMarket.getLimitOrders();
            for(Order order : orders)
            {
                if(passesFilter(input, order) && hasReadPermission(playerSender, isAdmin, bankManager, bankAccountMembershipCache, order))
                {
                    orderData.add(order);
                }
            }
        }
        else
        {
            List<ItemID> marketIDs = serverMarketManager.getAvailableMarketIDs();
            for(ItemID marketID : marketIDs)
            {
                List<Order> orders = serverMarketManager.getMarket(marketID).getLimitOrders();
                for(Order order : orders)
                {
                    if(passesFilter(input, order) && hasReadPermission(playerSender, isAdmin, bankManager, bankAccountMembershipCache, order))
                    {
                        orderData.add(order);
                    }
                }
            }
        }
        future.complete(new OutputData(orderData));
        return future;
    }

    private boolean passesFilter(InputData input, Order order)
    {
        int bankAccountNr = order.getBankAccountNr();
        if(input.bankAccountNr > 0 && bankAccountNr != input.bankAccountNr)
            return false;
        long timestamp = order.getTime();
        if(input.timeBegin > timestamp || timestamp > input.timeEnd)
            return false;
        return input.executorPlayer == null || input.executorPlayer.equals(order.getExecutorPlayerUUID());
    }

    private boolean hasReadPermission(UUID sender, boolean isAdmin, IBankManager bankManager,
                                      Map<Integer, Boolean> cache, Order order)
    {
        if(isAdmin)
            return true;
        if(bankManager == null)
            return false;
        int bankAccountNr = order.getBankAccountNr();
        Boolean cached = cache.get(bankAccountNr);
        if(cached != null)
            return cached;
        ISyncServerBankAccount bankAccount = bankManager.getSync().getBankAccount(bankAccountNr);
        boolean isMember = false;
        if(bankAccount != null)
        {
            BankUserData userData = bankAccount.getUserData(sender);
            isMember = userData != null;
        }
        cache.put(bankAccountNr, isMember);
        return isMember;
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
