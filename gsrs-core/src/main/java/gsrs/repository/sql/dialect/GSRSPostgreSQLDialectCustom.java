package gsrs.repository.sql.dialect;

import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.type.descriptor.sql.BinaryTypeDescriptor;
import org.hibernate.type.descriptor.sql.LongVarcharTypeDescriptor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;

/*
Designed by Tyler Peryea
*/
public class GSRSPostgreSQLDialectCustom extends PostgreSQLDialect {

    public GSRSPostgreSQLDialectCustom() {
        super();

        registerColumnType(java.sql.Types.BLOB, "bytea");
        registerColumnType(java.sql.Types.CLOB, "text");
    }

    @Override
    public SqlTypeDescriptor remapSqlTypeDescriptor(SqlTypeDescriptor sqlTypeDescriptor) {
        if (sqlTypeDescriptor.getSqlType() == java.sql.Types.BLOB) {
            return BinaryTypeDescriptor.INSTANCE;
        }else if (sqlTypeDescriptor.getSqlType() == java.sql.Types.CLOB) {
            return LongVarcharTypeDescriptor.INSTANCE;
        }
        return super.remapSqlTypeDescriptor(sqlTypeDescriptor);
    }
}
