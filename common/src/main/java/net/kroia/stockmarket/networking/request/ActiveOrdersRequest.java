package net.kroia.stockmarket.networking.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * @brief
 * Requests for pending orders which are waiting in the order book.
 * The current implementation does not check if the requestor is permitted to read the order data.
 * todo: Implement a permission check to only send orders which the requestor is allowed to see.
 *       - Only orders that belong to bank accounts the requestor has access to
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
        /*boolean hasPermission = playerIsAdmin(sender);
        IServerBankManager bankManager = getServerBankManager();
        if(!hasPermission)
        {
            if(input.executorPlayer != null)
            {
                if(input.bankAccountNr > 0)
                {
                    IBankAccount bankAccount = bankManager.getBankAccount(input.bankAccountNr);
                    if(bankAccount == null)
                    {
                        // Bank account does not exist
                        return new OutputData(orders);
                    }
                    if(!bankAccount.hasUser(sender.getUUID()))
                    {
                        // The requestor is not a member of the bank account and therefore is not permitted
                        // to request any order information from that account
                        return new OutputData(orders);
                    }

                    return getFiltered(input, sender);
                }
                else
                {
                    List<IBankAccount> bankAccounts = bankManager.getBankAccounts(input.executorPlayer);
                    for(IBankAccount bankAccount : bankAccounts)
                    {
                        if(bankAccount.hasUser(sender.getUUID()))
                        {
                            return getFiltered(input, sender);
                        }
                    }
                }
            }
            else
            {
                if(input.bankAccountNr > 0)
                {

                }
                else
                {

                }
            }
        }

        if(!hasPermission && input.executorPlayer != null)
        {
            hasPermission = sender.getUUID().equals(input.executorPlayer);
        }

        if(!hasPermission && input.bankAccountNr > 0)
        {
            // Check if the requestor is a member of the given bank account
            IBankAccount bankAccount = bankManager.getBankAccount(input.bankAccountNr);
            if(bankAccount == null)
            {
                // Bank account does not exist
                return new OutputData(orders);
            }

            if(!bankAccount.hasUser(sender.getUUID()))
            {
                // The requestor is not a member of the bank account and therefore is not permitted
                // to request any order information from that account
                return new OutputData(orders);
            }
            hasPermission = true;
        }

        ServerMarketManager serverMarketManager = getServerMarketManager();*/
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
                if(passesFilter(input, playerSender, order))
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
                    if(passesFilter(input, playerSender, order))
                    {
                        orderData.add(order);
                    }
                }
            }
        }
        future.complete(new OutputData(orderData));
        return future;
    }

    private boolean passesFilter(InputData input, UUID sender, Order order)
    {
        int bankAccountNr = order.getBankAccountNr();
        if(input.bankAccountNr > 0 && bankAccountNr != input.bankAccountNr)
            return false;
        long timestamp = order.getTime();
        if(input.timeBegin > timestamp || timestamp > input.timeEnd)
            return false;
        return input.executorPlayer == null || input.executorPlayer.equals(sender);
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
