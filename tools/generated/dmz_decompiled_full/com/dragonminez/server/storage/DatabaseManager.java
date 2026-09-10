package com.dragonminez.server.storage;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

public class DatabaseManager implements IDataStorage {
   private static final Pattern VALID_TABLE_NAME = Pattern.compile("^[A-Za-z0-9_]+$");
   private static final String DEFAULT_TABLE = "player_data";
   private HikariDataSource dataSource;
   private boolean isConnected = false;

   private static String sanitizeTableName(String tableName) {
      if (tableName != null && VALID_TABLE_NAME.matcher(tableName).matches()) {
         return tableName;
      } else {
         LogUtil.error(Env.SERVER, "Invalid storage table name '" + tableName + "'; falling back to 'player_data'.");
         return "player_data";
      }
   }

   @Override
   public void init() {
      GeneralServerConfig.StorageConfig config = ConfigManager.getServerConfig().getStorage();
      if (!this.hasValidCredentials(config)) {
         LogUtil.error(Env.SERVER, "DATABASE ERROR: Missing credentials (Host, DB Name, User or Password).");
         LogUtil.error(Env.SERVER, "FALLBACK: System will use Default Local NBT Storage.");
         this.isConnected = false;
      } else {
         LogUtil.info(Env.SERVER, "Connecting to Database: " + config.getHost() + ":" + config.getPort());
         HikariConfig hikariConfig = new HikariConfig();
         String jdbcUrl = "jdbc:mariadb://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabase();
         hikariConfig.setJdbcUrl(jdbcUrl);
         hikariConfig.setUsername(config.getUsername());
         hikariConfig.setPassword(config.getPassword());
         hikariConfig.setMaximumPoolSize(config.getPoolSize());
         hikariConfig.setPoolName("DragonMineZ-Pool");
         hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
         hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
         hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
         hikariConfig.setConnectionTimeout(5000L);

         try {
            this.dataSource = new HikariDataSource(hikariConfig);
            this.createTable(sanitizeTableName(config.getTable()));
            this.isConnected = true;
            LogUtil.info(Env.SERVER, "Database connected successfully!");
         } catch (Exception var5) {
            LogUtil.error(Env.SERVER, "CRITICAL: Failed to connect to database: " + var5.getMessage());
            LogUtil.error(Env.SERVER, "FALLBACK: System will use Default Local NBT Storage to prevent data loss.");
            this.isConnected = false;
         }
      }
   }

   private boolean hasValidCredentials(GeneralServerConfig.StorageConfig config) {
      return !config.getHost().isEmpty() && !config.getDatabase().isEmpty() && !config.getUsername().isEmpty() && !config.getPassword().isEmpty();
   }

   private void createTable(String tableName) {
      String sql = "CREATE TABLE IF NOT EXISTS "
         + tableName
         + " (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(64), data MEDIUMBLOB, last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP);";

      try (
         Connection conn = this.dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql);
      ) {
         stmt.execute();
      } catch (SQLException var11) {
         LogUtil.error(Env.SERVER, "Error creating table: " + var11.getMessage());
         this.isConnected = false;
      }
   }

   @Override
   public boolean saveData(UUID uuid, String name, CompoundTag tag) {
      if (this.isConnected && this.dataSource != null) {
         String tableName = sanitizeTableName(ConfigManager.getServerConfig().getStorage().getTable());
         String sql = "INSERT INTO "
            + tableName
            + " (uuid, name, data) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE name = ?, data = ?, last_updated = CURRENT_TIMESTAMP";

         try {
            boolean var9;
            try (
               Connection conn = this.dataSource.getConnection();
               PreparedStatement stmt = conn.prepareStatement(sql);
            ) {
               byte[] dataBytes = this.nbtToBytes(tag);
               stmt.setString(1, uuid.toString());
               stmt.setString(2, name);
               stmt.setBytes(3, dataBytes);
               stmt.setString(4, name);
               stmt.setBytes(5, dataBytes);
               stmt.executeUpdate();
               var9 = true;
            }

            return var9;
         } catch (SQLException var14) {
            LogUtil.error(Env.SERVER, "Failed to save player " + name + " to DB: " + var14.getMessage());
            return false;
         }
      } else {
         return false;
      }
   }

   @Override
   public CompoundTag loadData(UUID uuid) {
      if (this.isConnected && this.dataSource != null) {
         String tableName = sanitizeTableName(ConfigManager.getServerConfig().getStorage().getTable());
         String sql = "SELECT data FROM " + tableName + " WHERE uuid = ?";

         try (
            Connection conn = this.dataSource.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
         ) {
            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
               if (!rs.next()) {
                  return null;
               }

               try (InputStream is = rs.getBinaryStream("data")) {
                  if (is != null) {
                     return NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
                  }
               } catch (IOException var15) {
                  LogUtil.error(Env.SERVER, "Error decompressing NBT for " + uuid + ": " + var15.getMessage());
               }
            }
         } catch (SQLException var19) {
            LogUtil.error(Env.SERVER, "Failed to load player " + uuid + " from DB: " + var19.getMessage());
         }

         return null;
      } else {
         return null;
      }
   }

   @Override
   public void shutdown() {
      if (this.dataSource != null && !this.dataSource.isClosed()) {
         this.dataSource.close();
         LogUtil.info(Env.SERVER, "Database connection closed.");
      }
   }

   @Override
   public String getName() {
      return "DATABASE (MariaDB/MySQL)";
   }

   private byte[] nbtToBytes(CompoundTag tag) {
      try {
         byte[] var3;
         try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(tag, outputStream);
            var3 = outputStream.toByteArray();
         }

         return var3;
      } catch (IOException var7) {
         LogUtil.error(Env.SERVER, "Error serializing NBT: " + var7.getMessage());
         return new byte[0];
      }
   }
}
