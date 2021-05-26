package Services;

import java.sql.*;
import java.util.TreeMap;
import java.util.function.Consumer;

import Models.*;

public class ItemRepository {

    private Connection conn;
    private TreeMap<ItemCategory, Category> items;
    private static ItemRepository repo = null;

    private ItemRepository() {
        conn = Database.getConnection();
        items = readItems();
    }

    public static ItemRepository getRepo(){
        if(repo == null)
            repo = new ItemRepository();
        return repo;
    }

    public TreeMap<ItemCategory, Category> getItems(){
        return this.items;
    }

    // create
    public void insertItem(Item item)
    {
        try {
            CallableStatement cst = conn.prepareCall("{call insertItem(?,?,?)}");
            cst.registerOutParameter(1, Types.INTEGER); //out param (result of the procedure call)
            cst.setString(2, item.getName());
            cst.setString(3, item.getDescription());
            cst.setDouble(4, item.getStartingPrice());
            cst.setString(5, item.getCategory().name());
            cst.setInt(6, item.getSeller().getId());
            cst.execute();
            item.setId(cst.getInt(1));

            switch (item.getCategory()) {
                case Antique -> {
                    PreparedStatement st = conn.prepareStatement("insert into antiqueitems(id, age) values (?, ?);");
                    var iitem = (AntiqueItem)item;
                    st.setInt(1, iitem.getId());
                    st.setInt(2, iitem.getAge());
                }
                case Company -> {
                    PreparedStatement st = conn.prepareStatement("insert into companyitems(id, stock) values (?, ?);");
                    var iitem = (CompanyItem) item;
                    st.setInt(1, iitem.getId());
                    st.setInt(2, iitem.getStockAmount());
                }
                case Art -> {
                    PreparedStatement st = conn.prepareStatement("insert into artitems(id, author, art_type) values (?, ?, ?);");
                    var iitem = (ArtItem)item;
                    st.setInt(1, iitem.getId());
                    st.setString(2, iitem.getAuthor());
                    st.setString(3, iitem.getType());
                }
            }
            items.get(item.getCategory()).getItems().add(item);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // read
    public Item getItemById(int id)
    {
        try {
            PreparedStatement pst = conn.prepareStatement("SELECT * FROM items WHERE id=?");
            pst.setInt(1, id);

            ResultSet resultSet = pst.executeQuery();
            return mapToItem(resultSet);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public TreeMap<ItemCategory, Category> readItems()
    {
        var items = new TreeMap<ItemCategory, Category>();
        var artItems = items.put(ItemCategory.Art, new Category(ItemCategory.Art));
        var antiqueItems = items.put(ItemCategory.Antique, new Category(ItemCategory.Antique));
        var companyItems = items.put(ItemCategory.Company, new Category(ItemCategory.Company));

        try {
            PreparedStatement st = conn.prepareStatement("select id from items;");
            var res = st.executeQuery();
            while(res.next()){
                int id = res.getInt(1);
                var item = getItemById(id);
                switch (item.getCategory()){
                    case Antique -> antiqueItems.getItems().add(item);
                    case Company -> companyItems.getItems().add(item);
                    case Art -> artItems.getItems().add(item);
                }
            }
            return items;
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    // update
    public void updateItem(int id, int price, int buyerId)
    {
        try {
            PreparedStatement st = conn.prepareStatement("UPDATE items SET buyingPrice=?, buyer_id=? WHERE id=?");
            st.setInt(1, price);
            st.setInt(2, buyerId);
            st.setInt(3, id);

            st.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // delete
    public void deleteItem(int id)
    {
        try {
            PreparedStatement st = conn.prepareStatement("DELETE FROM items WHERE id=?");
            st.setInt(1, id);

            st.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Item mapToItem(ResultSet resultSet) throws SQLException
    {
        if (resultSet.next()){
            Consumer<Item> setupItem = (item) -> {
                try {
                    item.setId(resultSet.getInt(1));
                    item.setName(resultSet.getString(2));
                    item.setDescription(resultSet.getString(3));
                    item.setStartingPrice(resultSet.getInt(4));
                    item.setBuyingPrice(resultSet.getInt(5));
                    item.setCategory(ItemCategory.valueOf(resultSet.getString(6)));
                    final int userId1 = resultSet.getInt(7);
                    if(userId1 != -1){
                        var buyer = UserRepository.getRepo().getUsers().stream().filter(user -> user.getId() == userId1).findFirst();
                        item.setBuyer(buyer.get());
                    } else {
                        item.setBuyer(null);
                    }
                    final int userId2 = resultSet.getInt(8);
                    var seller = UserRepository.getRepo().getUsers().stream().filter(user -> user.getId() == userId2).findFirst();
                    item.setSeller(seller.get());

                } catch (Exception e){
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            };
            switch (ItemCategory.valueOf(resultSet.getString(6))){
                case Antique -> {
                    var item = new AntiqueItem();
                    setupItem.accept(item);

                    PreparedStatement st = conn.prepareStatement("select * from antiqueitems where id=?");
                    st.setInt(1, item.getId());
                    var res = st.executeQuery();
                    item.setAge(res.getInt(1));
                    return item;
                }
                case Company -> {
                    var item = new CompanyItem();
                    setupItem.accept(item);

                    PreparedStatement st = conn.prepareStatement("select * from companyitems where id=?");
                    st.setInt(1, item.getId());
                    var res = st.executeQuery();
                    item.setStockAmount(res.getInt(1));
                    return item;
                }
                case Art -> {
                    var item = new ArtItem();
                    setupItem.accept(item);

                    PreparedStatement st = conn.prepareStatement("select * from artitems where id=?");
                    st.setInt(1, item.getId());
                    var res = st.executeQuery();
                    item.setAuthor(res.getString(1));
                    item.setType(res.getString(2));
                    return item;
                }
            }
        }
        return null;
    }
}
