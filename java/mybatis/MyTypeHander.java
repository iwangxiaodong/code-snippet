package company.x.server.api.mapper;

import company.x.server.domain.GatewayId;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeHandler;

@MappedTypes(GatewayId.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class MyTypeHander implements TypeHandler<GatewayId> {

    @Override
    public void setParameter(PreparedStatement ps, int i, GatewayId t, JdbcType jt) throws SQLException {
        ps.setString(i, t.id());
    }

    @Override
    public GatewayId getResult(ResultSet rs, String columnName) throws SQLException {
        String id = rs.getString(columnName);
        return new GatewayId(id);
    }

    @Override
    public GatewayId getResult(ResultSet rs, int columnName) throws SQLException {
        String id = rs.getString(columnName);
        return new GatewayId(id);
    }

    @Override
    public GatewayId getResult(CallableStatement cs, int columnName) throws SQLException {
        String id = cs.getString(columnName);
        return new GatewayId(id);
    }

}
