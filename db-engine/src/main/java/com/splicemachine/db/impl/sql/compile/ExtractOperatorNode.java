/*
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified this file.
 *
 * All Splice Machine modifications are Copyright 2012 - 2016 Splice Machine, Inc.,
 * and are licensed to you under the License; you may not use this file except in
 * compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package com.splicemachine.db.impl.sql.compile;

import com.splicemachine.db.iapi.sql.compile.C_NodeTypes;

import com.splicemachine.db.iapi.types.TypeId;
import com.splicemachine.db.iapi.types.DateTimeDataValue;
import com.splicemachine.db.iapi.types.DataTypeDescriptor;

import com.splicemachine.db.iapi.sql.compile.TypeCompiler;

import com.splicemachine.db.iapi.reference.SQLState;
import com.splicemachine.db.iapi.error.StandardException;

import com.splicemachine.db.iapi.services.sanity.SanityManager;

import java.sql.Types;

import java.util.List;

/**
 * This node represents a unary extract operator, used to extract
 * a field from a date/time. The field value is returned as an integer.
 *
 */
public class ExtractOperatorNode extends UnaryOperatorNode {

	static private final String fieldName[] = {
		"YEAR", "QUARTER", "MONTH", "MONTHNAME", "WEEK", "WEEKDAY", "WEEKDAYNAME", "DAYOFYEAR", "DAY", "HOUR", "MINUTE", "SECOND"
	};
	static private final String fieldMethod[] = {
		"getYear","getQuarter","getMonth","getMonthName","getWeek","getWeekDay","getWeekDayName","getDayOfYear","getDate","getHours","getMinutes","getSeconds"
	};

    static private final long fieldCardinality[] = {
        5l, 4l, 12l, 12l, 52l, 7l, 7l, 365l, 31l, 24l, 60l, 60l
    };

	private int extractField;

	/**
	 * Initializer for a ExtractOperatorNode
	 *
	 * @param field		The field to extract
	 * @param operand	The operand
	 */
	public void init(Object field, Object operand) {
		extractField = ((Integer) field).intValue();
		super.init( operand,
					"EXTRACT "+fieldName[extractField],
					fieldMethod[extractField] );
	}

	/**
	 * Bind this operator
	 *
	 * @param fromList			The query's FROM list
	 * @param subqueryList		The subquery list being built as we find SubqueryNodes
	 * @param aggregateVector	The aggregate vector being built as we find AggregateNodes
	 *
	 * @return	The new top of the expression tree.
	 *
	 * @exception StandardException		Thrown on error
	 */
	@Override
	public ValueNode bindExpression(FromList fromList,
									SubqueryList subqueryList,
									List<AggregateNode> aggregateVector) throws StandardException  {
		int	operandType;
		TypeId opTypeId;

		bindOperand(fromList, subqueryList, aggregateVector);

		opTypeId = operand.getTypeId();
		operandType = opTypeId.getJDBCTypeId();

		/*
		** Cast the operand, if necessary, - this function is allowed only on
		** date/time types.  By default, we cast to DATE if extracting
		** YEAR, MONTH or DAY and to TIME if extracting HOUR, MINUTE or
		** SECOND.
		*/
		if (opTypeId.isStringTypeId())
		{
            TypeCompiler tc = operand.getTypeCompiler();
			int castType = (extractField < 9) ? Types.DATE : Types.TIME;
			operand =  (ValueNode)
				getNodeFactory().getNode(
					C_NodeTypes.CAST_NODE,
					operand, 
					DataTypeDescriptor.getBuiltInDataTypeDescriptor(castType, true, 
										tc.getCastToCharWidth(
												operand.getTypeServices())),
					getContextManager());
			((CastNode) operand).bindCastNodeOnly();

			opTypeId = operand.getTypeId();
			operandType = opTypeId.getJDBCTypeId();
		}

		if ( ! ( ( operandType == Types.DATE )
			   || ( operandType == Types.TIME ) 
			   || ( operandType == Types.TIMESTAMP ) 
			)	) {
			throw StandardException.newException(SQLState.LANG_UNARY_FUNCTION_BAD_TYPE, 
						"EXTRACT "+fieldName[extractField],
						opTypeId.getSQLTypeName());
		}

		/*
			If the type is DATE, ensure the field is okay.
		 */
		if ( (operandType == Types.DATE) 
			 && (extractField > DateTimeDataValue.DAY_FIELD) ) {
			throw StandardException.newException(SQLState.LANG_UNARY_FUNCTION_BAD_TYPE, 
						"EXTRACT "+fieldName[extractField],
						opTypeId.getSQLTypeName());
		}

		/*
			If the type is TIME, ensure the field is okay.
		 */
		if ( (operandType == Types.TIME) 
			 && (extractField < DateTimeDataValue.HOUR_FIELD) ) {
			throw StandardException.newException(SQLState.LANG_UNARY_FUNCTION_BAD_TYPE, 
						"EXTRACT "+fieldName[extractField],
						opTypeId.getSQLTypeName());
		}

		/*
		** The result type of extract is int,
		** unless it is TIMESTAMP and SECOND, in which case
		** for now it is DOUBLE but eventually it will need to
		** be DECIMAL(11,9).
		*/
		if ( (operandType == Types.TIMESTAMP)
			 && (extractField == DateTimeDataValue.SECOND_FIELD) ) {
			setType(new DataTypeDescriptor(
							TypeId.getBuiltInTypeId(Types.DOUBLE),
							operand.getTypeServices().isNullable()
						)
				);
		} else if (extractField == DateTimeDataValue.MONTHNAME_FIELD || extractField == DateTimeDataValue.WEEKDAYNAME_FIELD) {
            // name fields return varchar
            setType(new DataTypeDescriptor(
                        TypeId.CHAR_ID,
                        operand.getTypeServices().isNullable(),
                        14  // longest day name is in Portuguese (13); longest month name is in Greek (12)
                    )
            );
        } else {
			setType(new DataTypeDescriptor(
							TypeId.INTEGER_ID,
							operand.getTypeServices().isNullable()
						)
				);
		}

		return this;
	}

	public String toString() {
		if (SanityManager.DEBUG)
		{
			return "fieldName: " + fieldName[extractField] + "\n" +
				super.toString();
		}
		else
		{
			return "";
		}
	}

    @Override
    public long nonZeroCardinality(long numberOfRows) {
        return Math.min(fieldCardinality[extractField], numberOfRows);
    }
}
